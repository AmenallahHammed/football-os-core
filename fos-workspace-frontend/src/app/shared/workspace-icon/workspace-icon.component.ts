import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-workspace-calendar-icon',
  standalone: true,
  template: `
    <svg
      [attr.width]="size"
      [attr.height]="size"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      @switch (name) {
        @case ('calendar') {
          <path d="M8 2v4"></path>
          <path d="M16 2v4"></path>
          <rect x="3" y="4" width="18" height="18" rx="2"></rect>
          <path d="M3 10h18"></path>
        }
        @case ('activity') {
          <path d="M22 12h-4l-3 8-6-16-3 8H2"></path>
        }
        @case ('message-circle') {
          <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 8.5 8.5 0 0 1-4-.9L3 21l1.7-4.5a8.5 8.5 0 1 1 16.3-5"></path>
        }
        @case ('phone') {
          <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.3 1.7.6 2.5a2 2 0 0 1-.5 2.1L8 9.5a16 16 0 0 0 6.5 6.5l1.2-1.2a2 2 0 0 1 2.1-.5c.8.3 1.6.5 2.5.6a2 2 0 0 1 1.7 2"></path>
        }
        @case ('apps') {
          <rect x="3" y="3" width="7" height="7" rx="1"></rect>
          <rect x="14" y="3" width="7" height="7" rx="1"></rect>
          <rect x="3" y="14" width="7" height="7" rx="1"></rect>
          <rect x="14" y="14" width="7" height="7" rx="1"></rect>
        }
        @case ('folder') {
          <path d="M2 19a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-8l-2-2H4a2 2 0 0 0-2 2z"></path>
        }
        @case ('users') {
          <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="8.5" cy="7" r="4"></circle>
          <path d="M20 8v6"></path>
          <path d="M23 11h-6"></path>
        }
        @case ('house') {
          <path d="m3 11 9-8 9 8"></path>
          <path d="M5 10v10h14V10"></path>
          <path d="M9 20v-6h6v6"></path>
        }
        @case ('settings') {
          <circle cx="12" cy="12" r="3"></circle>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h.09a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
        }
        @case ('search') {
          <circle cx="11" cy="11" r="8"></circle>
          <path d="m21 21-4.3-4.3"></path>
        }
        @case ('chevron-left') {
          <path d="m15 18-6-6 6-6"></path>
        }
        @case ('chevron-right') {
          <path d="m9 18 6-6-6-6"></path>
        }
        @case ('chevron-down') {
          <path d="m6 9 6 6 6-6"></path>
        }
        @case ('check') {
          <path d="M20 6 9 17l-5-5"></path>
        }
        @case ('plus') {
          <path d="M12 5v14"></path>
          <path d="M5 12h14"></path>
        }
        @case ('x') {
          <path d="m18 6-12 12"></path>
          <path d="m6 6 12 12"></path>
        }
        @case ('upload') {
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
          <path d="M17 8l-5-5-5 5"></path>
          <path d="M12 3v12"></path>
        }
        @case ('file-text') {
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
          <path d="M14 2v6h6"></path>
          <path d="M16 13H8"></path>
          <path d="M16 17H8"></path>
          <path d="M10 9H8"></path>
        }
        @case ('clock') {
          <circle cx="12" cy="12" r="10"></circle>
          <path d="M12 6v6l4 2"></path>
        }
        @case ('map-pin') {
          <path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0"></path>
          <circle cx="12" cy="10" r="3"></circle>
        }
        @case ('trash') {
          <path d="M3 6h18"></path>
          <path d="M8 6V4h8v2"></path>
          <path d="M19 6l-1 14H6L5 6"></path>
          <path d="M10 11v6"></path>
          <path d="M14 11v6"></path>
        }
        @case ('more-vertical') {
          <circle cx="12" cy="5" r="1"></circle>
          <circle cx="12" cy="12" r="1"></circle>
          <circle cx="12" cy="19" r="1"></circle>
        }
        @case ('bell') {
          <path d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2a2 2 0 0 1-.6 1.4L4 17h5"></path>
          <path d="M10 21a2 2 0 0 0 4 0"></path>
        }
        @case ('inbox') {
          <path d="M22 12h-6l-2 3h-4l-2-3H2"></path>
          <path d="M5.4 5h13.2a2 2 0 0 1 2 1.6l1.4 7.4a2 2 0 0 1-2 2.4H3.9a2 2 0 0 1-2-2.4l1.4-7.4A2 2 0 0 1 5.4 5z"></path>
        }
      }
    </svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceCalendarIconComponent {
  @Input({ required: true }) name = '';
  @Input() size = 20;
}
