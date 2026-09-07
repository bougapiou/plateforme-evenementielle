import { Component, computed, input } from '@angular/core';

export interface Bar {
  label: string;
  value: number;
}

/** Minimal dependency-free SVG bar chart. */
@Component({
  selector: 'app-bar-chart',
  standalone: true,
  template: `
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" class="w-full" role="img">
      @for (b of bars(); track b.label; let i = $index) {
        <rect [attr.x]="x(i)" [attr.y]="barY(b.value)" [attr.width]="barWidth"
              [attr.height]="barHeight(b.value)" rx="3" class="fill-brand-500" />
        <text [attr.x]="x(i) + barWidth / 2" [attr.y]="height - 14" text-anchor="middle"
              class="fill-slate-400 text-[9px]">{{ short(b.label) }}</text>
        <text [attr.x]="x(i) + barWidth / 2" [attr.y]="barY(b.value) - 4" text-anchor="middle"
              class="fill-slate-600 text-[9px]">{{ b.value }}</text>
      }
      <line x1="0" [attr.y1]="height - 26" [attr.x2]="width" [attr.y2]="height - 26"
            class="stroke-slate-200" />
    </svg>
  `,
})
export class BarChartComponent {
  data = input.required<Bar[]>();
  width = 480;
  height = 180;

  bars = computed(() => this.data());
  private max = computed(() => Math.max(1, ...this.bars().map((b) => b.value)));

  get barWidth(): number {
    return Math.max(6, (this.width - 20) / Math.max(1, this.bars().length) - 10);
  }
  x(i: number): number {
    return 10 + i * ((this.width - 20) / Math.max(1, this.bars().length));
  }
  barHeight(v: number): number {
    return ((this.height - 40) * v) / this.max();
  }
  barY(v: number): number {
    return this.height - 26 - this.barHeight(v);
  }
  short(label: string): string {
    return label.length > 8 ? label.slice(0, 7) + '…' : label;
  }
}
