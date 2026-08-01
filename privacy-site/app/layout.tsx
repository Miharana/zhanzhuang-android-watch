import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Privacy — 站桩 · Zhan Zhuang",
  description:
    "Privacy policy for the 站桩 · Zhan Zhuang Android and Wear OS apps.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
