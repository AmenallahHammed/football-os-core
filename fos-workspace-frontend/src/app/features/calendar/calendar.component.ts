import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { CalendarEvent } from '../../shared/models/event.model';

interface CalendarCell {
  date: Date;
  inCurrentMonth: boolean;
  events: CalendarEvent[];
}

type CalendarView = 'Day' | 'Week' | 'Month';

const DEFAULT_VISIBLE_MONTH = new Date(2026, 7, 1);
const DEFAULT_SELECTED_DAY = new Date(2026, 7, 12);

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss'
})
export class CalendarComponent implements OnInit, OnChanges {
  @Input() events: CalendarEvent[] = [];
  @Output() daySelected = new EventEmitter<Date>();

  protected readonly weekDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  protected readonly viewModes: CalendarView[] = ['Day', 'Week', 'Month'];

  protected visibleMonth = this.startOfMonth(DEFAULT_VISIBLE_MONTH);
  protected selectedDate = new Date(DEFAULT_SELECTED_DAY);
  protected cells: CalendarCell[] = [];

  ngOnInit(): void {
    this.buildMonthGrid();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['events']) {
      this.buildMonthGrid();
    }
  }

  protected previousMonth(): void {
    const previous = new Date(this.visibleMonth);
    previous.setMonth(previous.getMonth() - 1);
    this.visibleMonth = this.startOfMonth(previous);
    this.selectedDate = new Date(this.visibleMonth);
    this.buildMonthGrid();
  }

  protected nextMonth(): void {
    const next = new Date(this.visibleMonth);
    next.setMonth(next.getMonth() + 1);
    this.visibleMonth = this.startOfMonth(next);
    this.selectedDate = new Date(this.visibleMonth);
    this.buildMonthGrid();
  }

  protected eventTone(type: CalendarEvent['type']): string {
    if (type === 'Training') {
      return 'training';
    }

    if (type === 'Match' || type === 'Meeting') {
      return 'match';
    }

    if (type === 'Recovery' || type === 'Medical') {
      return 'recovery';
    }

    return 'academy';
  }

  protected chooseDay(cell: CalendarCell): void {
    this.selectedDate = cell.date;
    this.daySelected.emit(cell.date);
  }

  protected isSelected(date: Date): boolean {
    return this.toIsoDate(date) === this.toIsoDate(this.selectedDate);
  }

  protected isToday(date: Date): boolean {
    return this.toIsoDate(date) === this.toIsoDate(new Date());
  }

  protected get monthLabel(): string {
    return new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(this.visibleMonth).toUpperCase();
  }

  protected eventSummary(event: CalendarEvent): string {
    return `${event.time} - ${event.opponent ? `vs ${event.opponent}` : event.location}`;
  }

  private buildMonthGrid(): void {
    const monthStart = this.startOfMonth(this.visibleMonth);
    const firstWeekDay = this.getMondayIndex(monthStart);
    const gridStart = new Date(monthStart);
    gridStart.setDate(monthStart.getDate() - firstWeekDay);

    this.cells = Array.from({ length: 42 }, (_, index) => {
      const date = new Date(gridStart);
      date.setDate(gridStart.getDate() + index);

      return {
        date,
        inCurrentMonth: date.getMonth() === monthStart.getMonth(),
        events: this.events.filter((event) => event.date === this.toIsoDate(date))
      };
    });
  }

  private startOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  private getMondayIndex(date: Date): number {
    return (date.getDay() + 6) % 7;
  }

  private toIsoDate(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

}
