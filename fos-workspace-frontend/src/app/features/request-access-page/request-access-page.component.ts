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
  protected readonly expectations = [
    'Customized overview of Football OS for your club structure.',
    'Identify disconnected workflows holding your staff back.',
    'Walkthrough of Workspace, roles, services, and access model.',
    'No pressure — just a clear product conversation.'
  ];

  protected readonly trustPills = ['Club Operations', 'Coaching Staff', 'Admin Teams'];

  protected readonly ratings = [
    'Rated by Clubs',
    'Rated by Coaches',
    'Rated by Analysts',
    'Rated by Admin Teams'
  ];

  protected readonly faqs = [
    {
      question: 'What is Football OS?',
      answer:
        'Football OS is a digital operating system for football clubs. It connects workspace, roles, documents, events, performance, analysis, and administration into one organized platform.'
    },
    {
      question: 'Who is the demo for?',
      answer:
        'The demo is designed for club representatives, administrators, coaches, operations staff, and decision-makers evaluating Football OS for their organization.'
    },
    {
      question: 'Can individual team members create accounts?',
      answer:
        'No. Football OS accounts are managed at club level. Team members sign in using credentials provided by their club.'
    },
    {
      question: 'What will we cover during the demo?',
      answer:
        'We will walk through the platform structure, Workspace, role-based access, services, and how Football OS can fit your club operations.'
    }
  ];

  protected formModel = this.createEmptyFormModel();

  protected submitRequest(form: NgForm): void {
    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.submitted = true;
    this.formModel = this.createEmptyFormModel();
    form.resetForm(this.formModel);
  }

  private createEmptyFormModel(): {
    firstName: string;
    lastName: string;
    email: string;
    phoneNumber: string;
    clubName: string;
    clubSize: string;
    role: string;
    message: string;
  } {
    return {
      firstName: '',
      lastName: '',
      email: '',
      phoneNumber: '',
      clubName: '',
      clubSize: '',
      role: '',
      message: ''
    };
  }
}
