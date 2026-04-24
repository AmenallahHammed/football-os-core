import { Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges } from '@angular/core';
import { CalendarEvent } from '../../shared/models/event.model';

interface CalendarCell {
  date: Date;
  inCurrentMonth: boolean;
  events: CalendarEvent[];
}

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

  protected visibleMonth = this.startOfMonth(new Date());
  protected selectedDate = new Date();
  protected cells: CalendarCell[] = [];

  ngOnInit(): void {
    this.buildMonthGrid();
    this.daySelected.emit(this.selectedDate);
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
    this.buildMonthGrid();
  }

  protected nextMonth(): void {
    const next = new Date(this.visibleMonth);
    next.setMonth(next.getMonth() + 1);
    this.visibleMonth = this.startOfMonth(next);
    this.buildMonthGrid();
  }

  protected jumpToToday(): void {
    this.visibleMonth = this.startOfMonth(new Date());
    this.selectedDate = new Date();
    this.buildMonthGrid();
    this.daySelected.emit(this.selectedDate);
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
    return new Intl.DateTimeFormat('en-US', { month: 'long', year: 'numeric' }).format(this.visibleMonth);
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
    return date.toISOString().slice(0, 10);
  }

}
