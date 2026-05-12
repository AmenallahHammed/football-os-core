import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';
import { AbstractControl, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators, FormControl, FormGroup } from '@angular/forms';

export interface CreateFolderDialogPerson {
  id: string;
  name: string;
  secondaryInfo?: string;
}

export type FolderAccessType = 'private' | 'specific';

export interface CreateFolderFormValue {
  name: string;
  accessType: FolderAccessType;
  allowedUserIds: string[];
}

@Component({
  selector: 'app-create-folder-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './create-folder-dialog.component.html',
  styleUrl: './create-folder-dialog.component.scss'
})
export class CreateFolderDialogComponent implements AfterViewInit {
  @Input() people: CreateFolderDialogPerson[] = [];
  @Output() cancelled = new EventEmitter<void>();
  @Output() created = new EventEmitter<CreateFolderFormValue>();

  @ViewChild('dialogPanel', { static: true }) private dialogPanel!: ElementRef<HTMLElement>;
  @ViewChild('nameInput', { static: true }) private nameInput!: ElementRef<HTMLInputElement>;

  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100), this.trimmedRequiredValidator()]
    }),
    accessType: new FormControl<FolderAccessType>('private', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    allowedUserIds: new FormControl<string[]>([], {
      nonNullable: true
    })
  });

  ngAfterViewInit(): void {
    queueMicrotask(() => this.nameInput.nativeElement.focus());
  }

  protected get selectedAccessType(): FolderAccessType {
    return this.form.controls.accessType.value;
  }

  protected get nameControl(): FormControl<string> {
    return this.form.controls.name;
  }

  protected get peopleControl(): FormControl<string[]> {
    return this.form.controls.allowedUserIds;
  }

  protected get specificPeopleRequiredAndMissing(): boolean {
    return this.selectedAccessType === 'specific' && this.peopleControl.value.length === 0;
  }

  protected get canSubmit(): boolean {
    if (this.form.invalid) {
      return false;
    }

    if (this.selectedAccessType === 'specific') {
      return this.peopleControl.value.length > 0;
    }

    return true;
  }

  protected close(): void {
    this.cancelled.emit();
  }

  protected onScrimClick(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.close();
    }
  }

  protected onAccessTypeChanged(nextAccessType: FolderAccessType): void {
    this.form.controls.accessType.setValue(nextAccessType);
    if (nextAccessType === 'private') {
      this.form.controls.allowedUserIds.setValue([]);
    }
  }

  protected togglePerson(personId: string): void {
    const selected = this.peopleControl.value;
    const isSelected = selected.includes(personId);
    this.peopleControl.setValue(isSelected ? selected.filter((id) => id !== personId) : [...selected, personId]);
    this.peopleControl.markAsDirty();
    this.peopleControl.markAsTouched();
  }

  protected isPersonSelected(personId: string): boolean {
    return this.peopleControl.value.includes(personId);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (!this.canSubmit) {
      return;
    }

    const value = this.form.getRawValue();
    this.created.emit({
      name: value.name.trim(),
      accessType: value.accessType,
      allowedUserIds: value.allowedUserIds
    });
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.close();
  }

  protected onPanelKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') {
      return;
    }

    const focusable = this.focusableElements();
    if (focusable.length === 0) {
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = document.activeElement as HTMLElement | null;

    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
      return;
    }

    if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private focusableElements(): HTMLElement[] {
    const selectors = [
      'button:not([disabled])',
      '[href]',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])'
    ];

    return Array.from(this.dialogPanel.nativeElement.querySelectorAll<HTMLElement>(selectors.join(','))).filter(
      (element) => !element.hasAttribute('aria-hidden')
    );
  }

  private trimmedRequiredValidator(): ValidatorFn {
    return (control: AbstractControl<string>): ValidationErrors | null => {
      return control.value.trim().length > 0 ? null : { trimmedRequired: true };
    };
  }
}
