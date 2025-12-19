# blog/views.py
from functools import wraps
import re
from datetime import timedelta

from django.contrib import messages
from django.http import JsonResponse
from django.shortcuts import render, get_object_or_404, redirect
from django.utils import timezone

from .forms import PostForm
from .models import Post, SecurityKey

from rest_framework import viewsets
from rest_framework.parsers import JSONParser, FormParser, MultiPartParser
from .serializers import PostSerializer


# -------------------------
#   간단한 관리자 인증 데코레이터
# -------------------------
def admin_required(view_func):
    @wraps(view_func)
    def _wrapped(request, *args, **kwargs):
        if not request.session.get("is_admin_authenticated", False):
            return redirect("admin_login")
        return view_func(request, *args, **kwargs)
    return _wrapped


# -------------------------
#   관리자 보안키 로그인 / 로그아웃
# -------------------------
def admin_login(request):
    if request.method == "POST":
        key = request.POST.get("security_key", "").strip()

        if SecurityKey.objects.filter(secret=key).exists():
            request.session["is_admin_authenticated"] = True
            messages.success(request, "관리자 모드로 로그인되었습니다.")
            return redirect("post_list")
        else:
            messages.error(request, "보안키가 올바르지 않습니다.")

    return render(request, "blog/admin_login.html")


def admin_logout(request):
    request.session.pop("is_admin_authenticated", None)
    messages.info(request, "로그아웃 되었습니다.")
    return redirect("admin_login")


# -------------------------
#   HTML 템플릿(관리자용) 뷰
# -------------------------
@admin_required
def post_list(request):
    posts = (
        Post.objects.filter(published_date__lte=timezone.now())
        .order_by("-published_date")
    )
    return render(request, "blog/post_list.html", {"posts": posts})


@admin_required
def post_detail(request, pk):
    post = get_object_or_404(Post, pk=pk)
    return render(request, "blog/post_detail.html", {"post": post})


@admin_required
def post_new(request):
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES)
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.published_date = timezone.now()
            post.save()
            return redirect("post_detail", pk=post.pk)
    else:
        form = PostForm()

    return render(request, "blog/post_edit.html", {"form": form, "post": None})


@admin_required
def post_edit(request, pk):
    post = get_object_or_404(Post, pk=pk)

    if request.method == "POST":
        form = PostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.published_date = timezone.now()
            post.save()
            return redirect("post_detail", pk=post.pk)
    else:
        form = PostForm(instance=post)

    return render(request, "blog/post_edit.html", {"form": form, "post": post})


@admin_required
def post_delete(request, pk):
    post = get_object_or_404(Post, pk=pk)

    if request.method == "POST":
        post.delete()
        return redirect("post_list")

    return render(request, "blog/post_confirm_delete.html", {"post": post})


@admin_required
def js_test(request):
    return render(request, "blog/js_test.html")


# -------------------------
#   관리자 대시보드 (Chart.js용)
#   - 페이지: /dashboard/
#   - 데이터: /dashboard/data/
# -------------------------
def _parse_stats(text: str):
    if not text:
        return None

    def pick(pattern):
        m = re.search(pattern, text)
        return int(m.group(1)) if m else None

    total = pick(r"총\s*좌석\s*수:\s*(\d+)\s*석")
    seated = pick(r"착석\s*인원:\s*(\d+)\s*명")
    queue = pick(r"대기열\s*인원.*?:\s*(\d+)\s*명")
    remain = pick(r"남은\s*좌석:\s*(\d+)\s*석")

    return {"total": total, "seated": seated, "queue": queue, "remain": remain}


def _status_bucket(queue, remain, total):
    if total is None or remain is None or queue is None:
        return None
    if queue > 0 or remain <= 0:
        return "혼잡"
    if remain <= total * 0.3:
        return "보통"
    return "여유"


@admin_required
def admin_dashboard(request):
    return render(request, "blog/admin_dashboard.html")


@admin_required
def admin_dashboard_data(request):
    now = timezone.now()
    since = now - timedelta(hours=24)

    qs = (
        Post.objects.filter(published_date__gte=since, published_date__lte=now)
        .order_by("published_date")
    )

    labels = []
    remain_series = []
    queue_series = []

    event_counts = {}  # 10분 단위 카운트
    status_counts = {"여유": 0, "보통": 0, "혼잡": 0}

    latest_at = None
    latest_status = None
    anomalies = []

    for p in qs:
        dt = p.published_date
        latest_at = dt

        stats = _parse_stats(getattr(p, "text", "") or "")
        if not stats:
            continue

        total = stats["total"]
        remain = stats["remain"]
        queue = stats["queue"]

        labels.append(dt.strftime("%H:%M"))
        remain_series.append(remain if remain is not None else None)
        queue_series.append(queue if queue is not None else None)

        st = _status_bucket(queue, remain, total)
        if st:
            status_counts[st] += 1
            latest_status = st

        bucket = dt.replace(minute=(dt.minute // 10) * 10, second=0, microsecond=0)
        key = bucket.strftime("%H:%M")
        event_counts[key] = event_counts.get(key, 0) + 1

        # 이상치 감지
        if remain is not None and remain < 0:
            anomalies.append({"time": dt.strftime("%Y-%m-%d %H:%M:%S"), "issue": f"남은 좌석 음수({remain})"})

        if total is not None and remain is not None and (remain > total):
            anomalies.append({"time": dt.strftime("%Y-%m-%d %H:%M:%S"), "issue": f"남은 좌석이 총좌석 초과({remain}>{total})"})

        if queue is not None and queue < 0:
            anomalies.append({"time": dt.strftime("%Y-%m-%d %H:%M:%S"), "issue": f"대기열 음수({queue})"})

    event_labels = sorted(event_counts.keys())
    event_values = [event_counts[k] for k in event_labels]

    payload = {
        "latest_at": latest_at.strftime("%Y-%m-%d %H:%M:%S") if latest_at else None,
        "latest_status": latest_status,
        "summary": {
            "events_1h": Post.objects.filter(published_date__gte=now - timedelta(hours=1)).count(),
            "events_24h": Post.objects.filter(published_date__gte=since).count(),
        },
        "charts": {
            "line": {
                "labels": labels[-200:],
                "remain": remain_series[-200:],
                "queue": queue_series[-200:],
            },
            "events": {
                "labels": event_labels[-200:],
                "values": event_values[-200:],
            },
            "status": status_counts,
        },
        "anomalies": anomalies[-50:],
    }
    return JsonResponse(payload)


# -------------------------
#   DRF API (클라이언트 앱용)
#   -> 여기는 계속 공개 (앱에서 조회)
# -------------------------
class BlogImage(viewsets.ModelViewSet):
    queryset = Post.objects.all().order_by("-published_date")
    serializer_class = PostSerializer
    parser_classes = [JSONParser, FormParser, MultiPartParser]
