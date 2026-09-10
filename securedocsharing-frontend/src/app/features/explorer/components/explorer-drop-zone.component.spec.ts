import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExplorerDropZoneComponent } from './explorer-drop-zone.component';

describe('ExplorerDropZoneComponent', () => {
  let component: ExplorerDropZoneComponent;
  let fixture: ComponentFixture<ExplorerDropZoneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExplorerDropZoneComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ExplorerDropZoneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should default to non-drag-over state', () => {
    expect(component.isDragOver()).toBeFalse();
  });
});
