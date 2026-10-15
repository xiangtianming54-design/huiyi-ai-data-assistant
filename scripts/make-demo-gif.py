"""生成 demo.gif：标题帧 + M5 场景截图轮播（≤ 60s）。

用法：python scripts/make-demo-gif.py（需 pillow，截图由 scripts/e2e-m5.js 产出）
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMAGES = ROOT / "docs" / "images"
OUTPUT = IMAGES / "demo.gif"
SIZE = (1280, 800)
FRAME_MS = 2400

# 帧序列：标题 → 知识库 → 场景 1 → 场景 5 → 场景 2 → 场景 3 → 场景 4
FRAMES = [
    ("m5-knowledge.png", "知识库管理：上传 · 解析 · 向量化"),
    ("m5-s1-rag-answer.png", "场景 1：RAG 口径问答（引用来源）"),
    ("m5-s5-no-hit.png", "场景 5：未命中兜底"),
    ("m5-s2-top10.png", "场景 2：Text-to-SQL 销售排名（柱状图）"),
    ("m5-s3-channel.png", "场景 3：渠道分析"),
    ("m5-s4-blocked.png", "场景 4：SQL 安全拦截"),
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for candidate in ("msyh.ttc", "simhei.ttf", "simsun.ttc"):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


def make_title_frame() -> Image.Image:
    image = Image.new("RGB", SIZE, "#001529")
    draw = ImageDraw.Draw(image)
    title_font = load_font(64)
    sub_font = load_font(28)
    tag_font = load_font(22)

    draw.text((SIZE[0] // 2, 300), "HuiYi AI Data Assistant", font=title_font, fill="#ffffff", anchor="mm")
    draw.text((SIZE[0] // 2, 380), "会意 · 企业经营数据智能分析助手", font=sub_font, fill="#91caff", anchor="mm")
    draw.text((SIZE[0] // 2, 460), "Java + Python 协同 | RAG 知识库问答 | Text-to-SQL 数据分析",
              font=tag_font, fill="#69a9d6", anchor="mm")
    return image


def make_captioned_frame(filename: str, caption: str) -> Image.Image:
    shot = Image.open(IMAGES / filename).convert("RGB").resize(SIZE, Image.LANCZOS)
    frame = Image.new("RGB", (SIZE[0], SIZE[1] + 56), "#ffffff")
    frame.paste(shot, (0, 0))
    draw = ImageDraw.Draw(frame)
    draw.rectangle([0, SIZE[1], SIZE[0], SIZE[1] + 56], fill="#001529")
    draw.text((SIZE[0] // 2, SIZE[1] + 28), caption, font=load_font(24), fill="#ffffff", anchor="mm")
    return frame


def main() -> None:
    frames = [make_title_frame()]
    frames.extend(make_captioned_frame(name, caption) for name, caption in FRAMES)
    frames[0].save(
        OUTPUT,
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_MS,
        loop=0,
        optimize=True,
    )
    total_seconds = len(frames) * FRAME_MS / 1000
    print(f"demo.gif: {len(frames)} frames, {total_seconds:.1f}s, {OUTPUT.stat().st_size // 1024}KB")


if __name__ == "__main__":
    main()
