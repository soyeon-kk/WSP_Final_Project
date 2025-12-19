from django.db import models
from django.utils import timezone


class Post(models.Model):
    author = models.CharField(max_length=50, default="AnonymousUser", blank=True)
    title = models.CharField(max_length=200)
    text = models.TextField()
    created_date = models.DateTimeField(default=timezone.now)
    published_date = models.DateTimeField(blank=True, null=True)
    image = models.ImageField(upload_to='blog_image/', blank=True, null=True)

    def publish(self):
        self.published_date = timezone.now()
        self.save()

    def __str__(self):
        return self.title


class SecurityKey(models.Model):
    """
    관리자 전용 '보안키' 한 줄 저장용 모델
    label: 키 이름 (예: default)
    secret: 실제 보안키 문자열
    """
    label = models.CharField(max_length=50, unique=True)
    secret = models.CharField(max_length=255)

    def __str__(self):
        return self.label
