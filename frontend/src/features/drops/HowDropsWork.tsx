import bag from '../../assets/icons/bag.png';
import clock from '../../assets/icons/clock.png';
import gift from '../../assets/icons/gift.png';
import { BulbIcon } from '../../components/icons';

const steps = [
  { title: '1. Reserve instantly', body: 'Tap Buy the moment a drop goes live. Your unit is held the instant you are in.', icon: bag, bg: 'bg-mint-card', text: 'text-[#33403A]', width: 'w-[92px]' },
  { title: '2. Pay within 10 minutes', body: 'Unpaid reservations go back to stock, so someone else gets a chance.', icon: clock, bg: 'bg-rose-card', text: 'text-[#4A3340]', width: 'w-[104px]' },
  { title: '3. One per customer', body: 'Every drop is limited to one unit per account to keep things fair.', icon: gift, bg: 'bg-peach-card', text: 'text-[#4A3A33]', width: 'w-[112px]' },
];

export function HowDropsWork() {
  return (
    <section aria-labelledby="how-title" className="flex flex-col gap-5">
      <h2 id="how-title" className="flex items-center gap-2.5 font-display text-[30px] font-bold">
        How drops work <BulbIcon />
      </h2>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(260px,1fr))] gap-[18px]">
      {steps.map((step) => (
        <div key={step.title} className={`relative min-h-[132px] overflow-hidden rounded-3xl py-[22px] pl-[22px] pr-[118px] ${step.bg}`}>
          <h3 className="font-display text-xl font-bold">{step.title}</h3>
          <p className={`mt-2 text-[15px] leading-normal ${step.text}`}>{step.body}</p>
          <img src={step.icon} alt="" className={`fade-edges absolute bottom-1.5 right-3 ${step.width}`} />
        </div>
      ))}
      </div>
    </section>
  );
}
