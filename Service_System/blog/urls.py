from django.urls import path, include
from rest_framework import routers
from .views import (
    post_list,
    post_detail,
    post_new,
    post_edit,
    post_delete,
    js_test,
    BlogImage,
    admin_login,
    admin_logout,
    admin_dashboard,
    admin_dashboard_data
)

router = routers.DefaultRouter()
router.register(r"Post", BlogImage)

urlpatterns = [
    # HTML 페이지
    path("", post_list, name="post_list"),
    path("post/<int:pk>/", post_detail, name="post_detail"),
    path("post/new/", post_new, name="post_new"),
    path("post/<int:pk>/edit/", post_edit, name="post_edit"),
    path("post/<int:pk>/delete/", post_delete, name="post_delete"),
    path("js_test/", js_test, name="js_test"),
    path("dashboard/", admin_dashboard, name="admin_dashboard"),
    path("dashboard/data/", admin_dashboard_data, name="admin_dashboard_data"),

    # 관리자 로그인
    path("login/", admin_login, name="admin_login"),
    path("logout/", admin_logout, name="admin_logout"),

    # DRF API
    path("api_root/", include(router.urls)),
]
