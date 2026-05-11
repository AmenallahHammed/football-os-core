import { NgFor } from '@angular/common';
import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [NgFor, RouterLink],
  templateUrl: './landing-page.component.html',
  styleUrl: './landing-page.component.scss'
})
export class LandingPageComponent implements AfterViewInit, OnDestroy {
  readonly actorRoles: ReadonlyArray<{
    title: string;
    subtitle: string;
    description: string;
    tags: ReadonlyArray<string>;
  }> = [
    {
      title: 'Player',
      subtitle: 'Consumer of Profile app',
      description: 'Reads only their own data, forms, calendar, and club communications.',
      tags: ['Profile', 'Read-only']
    },
    {
      title: 'Video Analyst',
      subtitle: 'Match analysis',
      description: 'Uses Match View for video analysis, clip creation, annotations, and tactical breakdowns.',
      tags: ['Match View', 'Workspace']
    },
    {
      title: 'Data Analyst',
      subtitle: 'Data insights',
      description: 'Uses DataPerf and Scouting. Reads from the data warehouse for analytics and insights.',
      tags: ['DataPerf', 'Scouting']
    },
    {
      title: 'Head Coach',
      subtitle: 'Lead access',
      description: 'Full access to Coach Pad, Workspace events, and training data. Manages team operations.',
      tags: ['Coach Pad', 'Workspace', 'DataPerf']
    },
    {
      title: 'Assistant Coach',
      subtitle: 'Support coaching',
      description: 'Similar to Head Coach with scoped access. Supports training and match preparation.',
      tags: ['Coach Pad', 'Workspace']
    },
    {
      title: 'Physical Coach',
      subtitle: 'Training load',
      description: 'Focused on training GPS/load data. Monitors player physical performance and recovery.',
      tags: ['Coach Pad', 'DataPerf']
    },
    {
      title: 'Goalkeeper Coach',
      subtitle: 'Specialized coaching',
      description: 'Scoped to goalkeeper-specific data and training sessions. Specialized analytics access.',
      tags: ['Coach Pad', 'Match View']
    },
    {
      title: 'Club Admin',
      subtitle: 'Administration',
      description: 'Manages Workspace, users, and club configuration. Can have sub-roles for delegation.',
      tags: ['Workspace', 'Admin']
    },
    {
      title: 'Medical Staff',
      subtitle: 'Medical access',
      description: 'Accesses medical files and player health data. Sub-roles: doctor, physio, therapist.',
      tags: ['Medical', 'Workspace']
    }
  ];

  private readonly landingAnchorIds = new Set([
    'platform',
    'services',
    'why-clubs',
    'roles',
    'access',
    'final-call-to-action'
  ]);

  private anchorScrollTimeoutId: number | undefined;
  private fragmentSubscription: Subscription | undefined;

  constructor(
    private readonly elementRef: ElementRef<HTMLElement>,
    private readonly route: ActivatedRoute
  ) {}

  ngAfterViewInit(): void {
    this.fragmentSubscription = this.route.fragment.subscribe((fragment) => {
      if (fragment && this.landingAnchorIds.has(fragment)) {
        this.scheduleAnchorScroll(fragment, 'auto');
      }
    });

    this.scrollToCurrentHash('auto');
  }

  ngOnDestroy(): void {
    if (this.anchorScrollTimeoutId !== undefined) {
      window.clearTimeout(this.anchorScrollTimeoutId);
    }

    this.fragmentSubscription?.unsubscribe();
  }

  @HostListener('click', ['$event'])
  onLandingAnchorClick(event: MouseEvent): void {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return;
    }

    const anchor = (event.target as Element | null)?.closest<HTMLAnchorElement>('a[href^="#"]');
    if (!anchor) {
      return;
    }

    const anchorId = decodeURIComponent(anchor.hash.slice(1));
    if (!this.landingAnchorIds.has(anchorId)) {
      return;
    }

    event.preventDefault();
    history.pushState(null, '', `#${anchorId}`);
    this.scheduleAnchorScroll(anchorId, 'smooth');
  }

  @HostListener('window:hashchange')
  onHashChange(): void {
    this.scrollToCurrentHash('smooth');
  }

  private scrollToCurrentHash(behavior: ScrollBehavior): void {
    const anchorId = decodeURIComponent(window.location.hash.slice(1));
    if (!this.landingAnchorIds.has(anchorId)) {
      return;
    }

    this.scheduleAnchorScroll(anchorId, behavior);
  }

  private scheduleAnchorScroll(anchorId: string, behavior: ScrollBehavior): void {
    if (this.anchorScrollTimeoutId !== undefined) {
      window.clearTimeout(this.anchorScrollTimeoutId);
    }

    this.scrollToAnchor(anchorId, behavior);

    window.requestAnimationFrame(() => {
      this.scrollToAnchor(anchorId, behavior);

      // Re-apply once after render/layout settles so direct URL hashes land reliably.
      this.anchorScrollTimeoutId = window.setTimeout(() => {
        this.scrollToAnchor(anchorId, 'auto');
      }, 120);
    });
  }

  private scrollToAnchor(anchorId: string, behavior: ScrollBehavior): void {
    const target = document.getElementById(anchorId);
    if (!target) {
      return;
    }

    const top = target.getBoundingClientRect().top + window.scrollY - this.getAnchorOffset();
    window.scrollTo({
      top: Math.max(0, top),
      behavior
    });
  }

  private getAnchorOffset(): number {
    const rawOffset = getComputedStyle(this.elementRef.nativeElement)
      .getPropertyValue('--landing-anchor-offset')
      .trim();
    const parsedOffset = Number.parseFloat(rawOffset);

    return Number.isFinite(parsedOffset) ? parsedOffset : 88;
  }
}
