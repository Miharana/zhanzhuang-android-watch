import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy — 站桩 · Zhan Zhuang",
  description:
    "Privacy policy for the 站桩 · Zhan Zhuang Android and Wear OS apps.",
};

type PolicySectionProps = {
  number: string;
  title: string;
  titleZh: string;
  children: React.ReactNode;
};

function PolicySection({
  number,
  title,
  titleZh,
  children,
}: PolicySectionProps) {
  return (
    <section className="policy-section" aria-labelledby={`section-${number}`}>
      <div className="section-heading">
        <span className="section-number" aria-hidden="true">
          {number}
        </span>
        <h2 id={`section-${number}`}>
          {title}
          <span>{titleZh}</span>
        </h2>
      </div>
      <div className="section-copy">{children}</div>
    </section>
  );
}

export default function Home() {
  return (
    <main>
      <header className="hero">
        <div className="sparkle-field" aria-hidden="true">
          <i />
          <i />
          <i />
        </div>
        <div className="hero-inner">
          <p className="eyebrow">Android · Wear OS</p>
          <div className="seal" aria-hidden="true">
            站
          </div>
          <h1>
            Privacy Policy
            <span>隐私政策</span>
          </h1>
          <p className="intro">
            站桩 · Zhan Zhuang is a quiet timer for standing meditation.
            Your practice stays yours.
          </p>
          <p className="intro-zh">
            站桩是一款安静的站桩计时器。你的练习数据，始终属于你。
          </p>
          <div className="effective-date">
            <span>Effective date</span>
            <strong>1 August 2026</strong>
            <span>生效日期</span>
          </div>
        </div>
      </header>

      <article className="policy-card">
        <p className="scope-note">
          This policy applies to the Android phone and Wear OS watch apps with
          package name <code>app.zhanzhuang.timer</code>.
          <span>
            本政策适用于包名为 <code>app.zhanzhuang.timer</code> 的 Android
            手机及 Wear OS 手表应用。
          </span>
        </p>

        <PolicySection
          number="01"
          title="Data kept locally"
          titleZh="本地保存的数据"
        >
          <p>
            The app stores your timer configuration and session history locally
            on your phone. A paired watch stores the session data needed to
            continue a timer and sends completed session information to the
            paired phone when it can reconnect.
          </p>
          <p lang="zh-CN">
            应用会在手机本地保存计时设置和练习记录。配对手表会保存继续计时所需的会话数据，并在重新连接后把已完成的会话信息发送到配对手机。
          </p>
          <p>
            If you grant the optional heart-rate permission on a Wear OS watch,
            the watch can record informational heart-rate samples during that
            session. This information is not medical advice.
          </p>
          <p lang="zh-CN">
            如果你在 Wear OS 手表上授予可选的心率权限，手表可以在练习期间记录供参考的心率样本。这些信息不构成医疗建议。
          </p>
          <p>
            The app has no account system and does not send your data to a
            developer-operated cloud service. It does not use advertising,
            analytics, tracking SDKs, or sell personal data.
          </p>
          <p lang="zh-CN">
            应用没有账户系统，也不会把你的数据发送到开发者运营的云服务。应用不含广告、分析或追踪 SDK，也不会出售个人数据。
          </p>
        </PolicySection>

        <PolicySection
          number="02"
          title="Optional Health Connect writing"
          titleZh="可选的 Health Connect 写入"
        >
          <p>
            Health Connect access is optional. When you explicitly grant write
            permissions, the phone app can write a completed mindfulness
            session to Health Connect. On devices where that record type is not
            supported, it writes an exercise-session fallback. Available
            heart-rate samples can also be written.
          </p>
          <p lang="zh-CN">
            Health Connect 权限完全可选。只有在你明确授予写入权限后，手机应用才会把已完成的正念会话写入 Health Connect；在不支持该记录类型的设备上，则回退写入运动会话。可用的心率样本也可以一并写入。
          </p>
          <p>
            The app does not read health data from Health Connect or from other
            apps. Refusing or later revoking this permission leaves the basic
            local timer available.
          </p>
          <p lang="zh-CN">
            应用不会从 Health Connect 或其他应用读取健康数据。拒绝或之后撤销该权限，不会影响基础的本地计时功能。
          </p>
        </PolicySection>

        <PolicySection
          number="03"
          title="Retention and deletion"
          titleZh="保留与删除"
        >
          <p>
            Local records remain on the phone or paired watch until you clear
            the app&apos;s storage in Android system settings or remove the app
            and its data.
          </p>
          <p lang="zh-CN">
            本地记录会保留在手机或配对手表上，直到你在 Android 系统设置中清除应用存储，或卸载应用并删除其数据。
          </p>
          <p>
            Data previously written to Health Connect is governed by your
            Health Connect controls and can be removed there. Uninstalling the
            app does not automatically delete records you separately chose to
            write to Health Connect.
          </p>
          <p lang="zh-CN">
            已写入 Health Connect 的数据由你的 Health Connect 设置管理，并可在那里删除。卸载应用不会自动删除你先前选择写入 Health Connect 的记录。
          </p>
        </PolicySection>

        <PolicySection number="04" title="Permissions" titleZh="权限">
          <p>
            The app requests only the permissions needed for its timer,
            notifications and haptics, optional foreground health session,
            optional heart-rate collection on the watch, and optional Health
            Connect writing.
          </p>
          <p lang="zh-CN">
            应用只会申请计时、通知与震动、可选的前台健康会话、手表可选心率采集，以及可选 Health Connect 写入所需的权限。
          </p>
          <p>
            It does not request location, contacts, camera, microphone, or
            internet access.
          </p>
          <p lang="zh-CN">
            应用不会申请位置、通讯录、相机、麦克风或互联网访问权限。
          </p>
        </PolicySection>

        <PolicySection number="05" title="Contact" titleZh="联系">
          <p>
            For privacy questions, use the public contact method shown in the
            app&apos;s Google Play listing.
          </p>
          <p lang="zh-CN">
            如有隐私问题，请使用应用 Google Play 商店详情页中公开的联系方式。
          </p>
        </PolicySection>

        <PolicySection number="06" title="Changes" titleZh="政策变更">
          <p>
            If this policy changes, the effective date and the published policy
            will be updated before the related app release.
          </p>
          <p lang="zh-CN">
            如果本政策发生变更，我们会在相关应用版本发布前更新生效日期和已发布的政策内容。
          </p>
        </PolicySection>
      </article>

      <footer>
        <div className="footer-mark" aria-hidden="true">
          <span />
          <b>静</b>
          <span />
        </div>
        <p>站桩 · Zhan Zhuang</p>
        <p className="footer-note">Quiet practice. Private by design.</p>
      </footer>
    </main>
  );
}
