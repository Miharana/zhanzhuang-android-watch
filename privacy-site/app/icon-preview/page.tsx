import type { Metadata } from "next";
import Image from "next/image";

export const metadata: Metadata = {
  title: "Icon directions — 站桩 · Zhan Zhuang",
  description: "Four launcher-icon directions for the Zhan Zhuang app.",
};

const directions = [
  { key: "E", name: "鎏金圆润", image: "/icon-preview/option-e.png", note: "金色实心太极最有温度；外圈的星芒最强。" },
  { key: "F", name: "拉丝金光", image: "/icon-preview/option-f.png", note: "太极的金属拉丝感最明显，整体较沉稳。" },
  { key: "G", name: "线刻微光", image: "/icon-preview/option-g.png", note: "三圆都更轻，最接近正式 Android Vector 的简洁感。" },
  { key: "H", name: "丰盈金印", image: "/icon-preview/option-h.png", note: "太极与下方双圆对比更强，缩小后仍有重量。" },
];

export default function IconPreviewPage() {
  return (
    <main className="icon-preview-page">
      <header className="icon-preview-header">
        <p>站桩 · ZHAN ZHUANG</p>
        <h1>图标方向 · II</h1>
        <span>上方太极、下方双圆；以附件的鎏金微光为质感参考</span>
      </header>
      <section aria-label="图标候选" className="icon-grid">
        {directions.map((direction) => (
          <article className="icon-card" key={direction.key}>
            <Image
              alt={`方案 ${direction.key}：${direction.name}`}
              className="icon-raster-preview"
              height={1024}
              src={direction.image}
              unoptimized
              width={1024}
            />
            <div className="icon-card-copy">
              <p>方案 {direction.key}</p>
              <h2>{direction.name}</h2>
              <span>{direction.note}</span>
            </div>
          </article>
        ))}
      </section>
      <footer className="icon-preview-footer">
        下方双圆形成倒品字。选定后会重新绘制为正式 Android Vector 图标，而非直接使用这些生成预览图。
      </footer>
    </main>
  );
}
