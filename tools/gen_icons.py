#!/usr/bin/env python3
"""生成 whattoeat 的应用图标与 README 用的 logo。

设计源自 app/src/main/res/drawable/ic_launcher.xml：
绿色圆角底 + 白色饭碗 + 两根筷子。

产物：
  docs/logo.png                              512x512（README 展示）
  app/src/main/res/mipmap-*/ic_launcher.png  各密度方形图标
  app/src/main/res/mipmap-*/ic_launcher_foreground.png  自适应图标前景
"""

import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
RES = os.path.join(ROOT, "app", "src", "main", "res")

GREEN = (47, 107, 63, 255)      # 品牌主色 #2F6B3F
GREEN_DARK = (36, 84, 49, 255)
WHITE = (255, 255, 255, 255)

SS = 4  # 超采样倍数


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def draw_icon(canvas_size, with_bg=True, bg_radius_ratio=0.22, safe_ratio=1.0, round_bg=False):
    """返回一张 size*size 的 RGBA 图标。

    with_bg=True  -> 绿色圆角底（普通图标）
    with_bg=False -> 透明底（自适应图标前景，图形收在安全区内）
    safe_ratio    -> 前景图形相对画布的缩放（自适应图标需要留安全区）
    round_bg      -> True 用整圆底，False 用圆角方底
    """
    s = canvas_size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if with_bg:
        if round_bg:
            d.ellipse([0, 0, s - 1, s - 1], fill=GREEN)
        else:
            d.rounded_rectangle(
                [0, 0, s - 1, s - 1], radius=int(s * bg_radius_ratio), fill=GREEN
            )

    # 前景几何按 108 单位坐标系定义（与原 vector 一致）
    u = s / 108.0 * safe_ratio
    ox = (s - 108 * u) / 2.0
    oy = (s - 108 * u) / 2.0

    def P(x, y):
        return (ox + x * u, oy + y * u)

    # 碗：半圆（圆心 (54,52)，半径 30）——对应原 vector 的 "M24,52h60a30,30 0 0,1 -60,0z"
    d.pieslice(
        [ox + 24 * u, oy + 22 * u, ox + 84 * u, oy + 82 * u],
        start=0, end=180, fill=WHITE,
    )

    # 筷子（画在碗之后，与原 vector 顺序一致）
    d.polygon([P(46, 30), P(50, 30), P(56, 56), P(52, 56)], fill=WHITE)
    d.polygon([P(60, 28), P(64, 28), P(66, 56), P(62, 56)], fill=WHITE)

    if with_bg:
        if round_bg:
            mask = Image.new("L", (s, s), 0)
            ImageDraw.Draw(mask).ellipse([0, 0, s - 1, s - 1], fill=255)
        else:
            mask = rounded_mask(s, int(s * bg_radius_ratio))
        img.putalpha(mask)
    return img.resize((canvas_size, canvas_size), Image.LANCZOS)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    # README logo：512x512，带透明圆角外缘
    logo = draw_icon(512, with_bg=True, bg_radius_ratio=0.22)
    save(logo, os.path.join(DOCS, "logo.png"))

    # 普通方形 launcher 图标（圆形底，Android 会自行裁切）
    for folder, size in [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ]:
        ic = draw_icon(size, with_bg=True, bg_radius_ratio=0.22, round_bg=True)
        save(ic, os.path.join(RES, folder, "ic_launcher.png"))

    # 自适应图标前景：图形收在中间 72/108 安全区内
    for folder, size in [
        ("mipmap-mdpi", 108),
        ("mipmap-hdpi", 162),
        ("mipmap-xhdpi", 216),
        ("mipmap-xxhdpi", 324),
        ("mipmap-xxxhdpi", 432),
    ]:
        fg = draw_icon(size, with_bg=False, safe_ratio=0.62)
        save(fg, os.path.join(RES, folder, "ic_launcher_foreground.png"))

    # 圆形变体（部分启动器会单独请求 round icon）
    for folder, size in [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ]:
        rc = draw_icon(size, with_bg=True, round_bg=True)
        save(rc, os.path.join(RES, folder, "ic_launcher_round.png"))


if __name__ == "__main__":
    main()
