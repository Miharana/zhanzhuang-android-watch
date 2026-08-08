import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Icon directions — 站桩 · Zhan Zhuang",
  description: "Four launcher-icon directions for the Zhan Zhuang app.",
};

type MarkProps = { label: string; children: React.ReactNode };

function Mark({ label, children }: MarkProps) {
  return (
    <svg aria-label={label} className="icon-mark" role="img" viewBox="0 0 240 240">
      <defs>
        <linearGradient id="gold" x1="35" x2="205" y1="26" y2="216">
          <stop stopColor="#E7D5A6" />
          <stop offset="0.45" stopColor="#C6A867" />
          <stop offset="1" stopColor="#A9863F" />
        </linearGradient>
      </defs>
      <circle className="icon-rim" cx="120" cy="120" r="102" />
      {children}
    </svg>
  );
}

function SealZhuangBlock() {
  return (
    <Mark label="方案 A：方折的篆体桩字">
      <g className="seal-stroke">
        <path d="M77 62V180M58 86H99M57 126H105M67 158H99" />
        <path d="M133 62V181M123 91H166M124 133H173M143 62L165 87M143 133L166 181" />
        <path d="M112 73H137M111 166H136" />
      </g>
    </Mark>
  );
}

function SealZhuangRound() {
  return (
    <Mark label="方案 B：圆转的篆体桩字">
      <g className="seal-stroke seal-stroke-round">
        <path d="M73 68C88 74 91 94 81 112C70 132 72 159 93 173" />
        <path d="M107 59V181M91 82H128M93 126H133M100 159H129" />
        <path d="M151 65C133 83 137 108 156 119C175 130 173 161 149 178" />
        <path d="M133 85H171M130 142H174" />
      </g>
    </Mark>
  );
}

function Taiji({ filled = true }: { filled?: boolean }) {
  return (
    <g>
      <circle className={filled ? "taiji-fill" : "taiji-outline"} cx="120" cy="145" r="45" />
      <path className="taiji-dark" d="M120 100a45 45 0 0 1 0 90a22.5 22.5 0 0 0 0-45a22.5 22.5 0 0 1 0-45Z" />
      <circle className="taiji-light-dot" cx="120" cy="122.5" r="7" />
      <circle className="taiji-dark-dot" cx="120" cy="167.5" r="7" />
    </g>
  );
}

function CircleTaijiOpen() {
  return (
    <Mark label="方案 C：品字三圆与阴阳">
      <circle className="small-ring" cx="82" cy="76" r="23" />
      <circle className="small-ring" cx="158" cy="76" r="23" />
      <Taiji />
    </Mark>
  );
}

function CircleTaijiSolid() {
  return (
    <Mark label="方案 D：品字三圆与阴阳印章">
      <circle className="small-disc" cx="82" cy="77" r="21" />
      <circle className="small-disc" cx="158" cy="77" r="21" />
      <Taiji filled={false} />
    </Mark>
  );
}

const directions = [
  { key: "A", name: "篆体桩 · 方折", note: "像一枚克制的方形古印；辨识重点是“桩”的骨架。", mark: <SealZhuangBlock /> },
  { key: "B", name: "篆体桩 · 圆转", note: "同样是“桩”，但线条更圆、更像静定时呼吸的流动。", mark: <SealZhuangRound /> },
  { key: "C", name: "品字三圆 · 阴阳", note: "两枚空圆在上，一枚略大的阴阳在下；最适合极小尺寸。", mark: <CircleTaijiOpen /> },
  { key: "D", name: "品字三圆 · 阴阳印", note: "三个金色实体形成印记感，下方阴阳保留为轮廓。", mark: <CircleTaijiSolid /> },
];

export default function IconPreviewPage() {
  return (
    <main className="icon-preview-page">
      <header className="icon-preview-header">
        <p>站桩 · ZHAN ZHUANG</p>
        <h1>图标方向</h1>
        <span>四个用于手机与 Pixel Watch 的同源图标方案</span>
      </header>
      <section aria-label="图标候选" className="icon-grid">
        {directions.map((direction) => (
          <article className="icon-card" key={direction.key}>
            {direction.mark}
            <div className="icon-card-copy">
              <p>方案 {direction.key}</p>
              <h2>{direction.name}</h2>
              <span>{direction.note}</span>
            </div>
          </article>
        ))}
      </section>
      <footer className="icon-preview-footer">
        选定后会制作为正式的 Android Vector 图标，并保持金色、土色与微光系统。
      </footer>
    </main>
  );
}
