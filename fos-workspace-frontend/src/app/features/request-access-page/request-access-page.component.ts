import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-request-access-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './request-access-page.component.html',
  styleUrl: './request-access-page.component.scss'
})
export class RequestAccessPageComponent {
  protected submitted = false;
  protected formModel = {
    fullName: '',
    clubName: '',
    role: '',
    workEmail: '',
    phoneNumber: '',
    message: ''
  };

  protected submitRequest(form: NgForm): void {
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.submitted = true;
    this.formModel = {
      fullName: '',
      clubName: '',
      role: '',
      workEmail: '',
      phoneNumber: '',
      message: ''
    };
    form.resetForm(this.formModel);
  }
}
