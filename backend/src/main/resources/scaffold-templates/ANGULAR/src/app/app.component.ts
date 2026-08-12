import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  template: `<h1>{{ title }}</h1><p>{{ description }}</p>`,
  styles: [`
    :host { display: block; font-family: system-ui, sans-serif; padding: 2rem; }
  `],
})
export class AppComponent {
  title = '{{projectName}}';
  description = '{{description}}';
}