from django.contrib import admin
from .models import Post, SecurityKey

admin.site.register(Post)
admin.site.register(SecurityKey)
