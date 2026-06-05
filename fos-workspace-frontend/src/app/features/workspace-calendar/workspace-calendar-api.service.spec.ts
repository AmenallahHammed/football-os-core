import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { WorkspaceCalendarApiService } from './workspace-calendar-api.service';

describe('WorkspaceCalendarApiService', () => {
  let service: WorkspaceCalendarApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [WorkspaceCalendarApiService]
    });

    service = TestBed.inject(WorkspaceCalendarApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
    TestBed.resetTestingModule();
  });

  it('sends event updates through the gateway URL', () => {
    service.updateEvent('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', { title: 'Updated Training' }).subscribe();

    const request = httpTesting.expectOne('http://localhost:8080/api/v1/events/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    expect(request.request.method).toBe('PUT');
    expect(request.request.url).not.toContain('8082');
    request.flush({});
  });

  it('sends event deletes through the gateway URL', () => {
    service.deleteEvent('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa').subscribe();

    const request = httpTesting.expectOne('http://localhost:8080/api/v1/events/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.url).not.toContain('8082');
    request.flush(null);
  });
});
