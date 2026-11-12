/** Decorative pastel clouds that close every page. */
export function CloudBand() {
  const puffs: [number, number, number, string][] = [
    [40, 150, 90, '#F6D9EC'], [150, 130, 70, '#FBCFE2'], [260, 160, 80, '#D9E4FB'], [420, 150, 72, '#F8D9EC'],
    [560, 170, 95, '#D3E0FA'], [720, 140, 78, '#FBD3E5'], [860, 165, 88, '#DCE7FC'], [1010, 140, 74, '#F7D2EA'],
    [1140, 160, 90, '#D6E2FA'], [1260, 130, 80, '#F9D4E8'],
  ];
  return (
    <div aria-hidden="true" className="mt-12">
      <svg width="100%" height="150" viewBox="0 0 1280 150" preserveAspectRatio="none" className="block">
        {puffs.map(([cx, cy, r, fill]) => <circle key={cx} cx={cx} cy={cy} r={r} fill={fill} />)}
        <path d="M90 52l3 10 10 3-10 3-3 10-3-10-10-3 10-3z" fill="#FFFFFF" />
        <path d="M330 40l3 10 10 3-10 3-3 10-3-10-10-3 10-3z" fill="#FFD25E" />
        <path d="M1080 30l3 10 10 3-10 3-3 10-3-10-10-3 10-3z" fill="#FFD25E" />
        <path d="M800 70l2 7 7 2-7 2-2 7-2-7-7-2 7-2z" fill="#FFFFFF" />
      </svg>
    </div>
  );
}
