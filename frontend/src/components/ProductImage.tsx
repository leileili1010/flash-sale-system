import { useState } from 'react';

interface ProductImageProps {
  src: string;
  alt: string;
  className?: string;
}

/** Product artwork with a friendly drawn placeholder when an image is missing or fails to load. */
export function ProductImage({ src, alt, className = '' }: ProductImageProps) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (!src || failedSrc === src) {
    return (
      <div role="img" aria-label={alt} className={`flex items-center justify-center bg-gradient-to-br from-pink-50 to-chip ${className}`}>
        <PlaceholderCritter />
      </div>
    );
  }
  return <img src={src} alt={alt} loading="lazy" className={`object-cover ${className}`} onError={() => setFailedSrc(src)} />;
}

function PlaceholderCritter() {
  return (
    <svg viewBox="0 0 120 120" className="h-3/5 max-h-40 w-auto" aria-hidden="true">
      <path d="M33 52 C22 34 22 12 30 1 C40 14 48 30 51 45 Z" fill="#8FB4FF" />
      <path d="M87 52 C98 34 98 12 90 1 C80 14 72 30 69 45 Z" fill="#8FB4FF" />
      <ellipse cx="60" cy="74" rx="40" ry="38" fill="#8FB4FF" />
      <ellipse cx="60" cy="76" rx="27" ry="23" fill="#FFE8DA" />
      <ellipse cx="48.5" cy="70" rx="6" ry="7.5" fill="#111A33" />
      <ellipse cx="71.5" cy="70" rx="6" ry="7.5" fill="#111A33" />
      <circle cx="50.5" cy="67" r="2.2" fill="#FFFFFF" />
      <circle cx="73.5" cy="67" r="2.2" fill="#FFFFFF" />
      <path d="M48 83 Q60 94 72 83" stroke="#111A33" strokeWidth="3" strokeLinecap="round" fill="none" />
    </svg>
  );
}
