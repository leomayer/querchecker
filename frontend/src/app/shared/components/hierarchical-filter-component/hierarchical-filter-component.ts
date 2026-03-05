import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal, computed, viewChild } from '@angular/core';
import { MatAutocompleteModule, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { FilterNode } from './hierarchical-filter-component.model';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  selector: 'app-hierarchical-filter',
  imports: [MatAutocompleteModule, MatInputModule, MatIconModule, MatFormFieldModule, MatButtonModule, MatChipsModule],
  templateUrl: './hierarchical-filter-component.html',
  styleUrl: './hierarchical-filter-component.scss',
})
export class HierarchicalFilterComponent {
  data = input.required<FilterNode[]>();
  label = input<string>('Auswahl');

  selectionChange = output<FilterNode | null>();

  private autoTrigger = viewChild.required(MatAutocompleteTrigger);
  private searchInput = viewChild.required<ElementRef<HTMLInputElement>>('searchInput');

  private sanitizer = inject(DomSanitizer);

  searchQuery = signal('');
  selectedPath = signal<FilterNode[]>([]);
  navStack = signal<FilterNode[]>([]);

  currentParent = computed(() => this.navStack().at(-1) ?? null);
  currentNodes = computed(() => this.currentParent()?.children ?? this.data());

  // Baut eine Map: node.id → Vorfahren-Array (für N Ebenen, Browse- und Such-Modus)
  private ancestorMap = computed(() => {
    const map = new Map<string, FilterNode[]>();
    const traverse = (nodes: FilterNode[], ancestors: FilterNode[] = []) => {
      for (const node of nodes) {
        map.set(node.id, ancestors);
        if (node.children?.length) traverse(node.children, [...ancestors, node]);
      }
    };
    traverse(this.data());
    return map;
  });

  private flatNodes = computed(() => {
    const flatten = (nodes: FilterNode[], parent?: string): FilterNode[] =>
      nodes.reduce((acc, node) => {
        const current = { ...node, parentName: parent };
        return acc.concat(current, flatten(node.children || [], node.name));
      }, [] as FilterNode[]);
    return flatten(this.data());
  });

  filteredResults = computed(() => {
    const query = this.searchQuery().toLowerCase();
    if (!query) return [];
    return this.flatNodes()
      .filter((node) => node.name.toLowerCase().includes(query))
      .sort((a, b) => {
        const aStart = a.name.toLowerCase().startsWith(query) ? 0 : 1;
        const bStart = b.name.toLowerCase().startsWith(query) ? 0 : 1;
        if (aStart !== bStart) return aStart - bStart;
        return a.level - b.level;
      });
  });

  // Input bleibt nach Auswahl leer – Chips zeigen die Selektion
  displayFn = (_node: FilterNode | null): string => '';

  isNodeSelected(node: FilterNode): boolean {
    return this.selectedPath().some((n) => n.id === node.id);
  }

  // Hebt den Suchtreffer fett hervor – sicher, da Daten aus eigenem Backend
  highlight(text: string, query: string): SafeHtml {
    if (!query) return text;
    const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const highlighted = text.replace(new RegExp(`(${escaped})`, 'gi'), '<strong>$1</strong>');
    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  onSelect(node: FilterNode | null): void {
    if (!node) return;
    const ancestors = this.ancestorMap().get(node.id) ?? [];
    this.selectedPath.set([...ancestors, node]);
    this.navStack.set([]);
    this.searchQuery.set('');
    this.selectionChange.emit(node);
    setTimeout(() => this.searchInput().nativeElement.blur(), 0);
  }

  // Entfernt ab Index (inkl.) – Vorfahren bleiben, Blatt wird abgewählt
  // Index 0 → alles löschen; Index 1 → nur letztes Level entfernen
  removeFromLevel(index: number): void {
    const newPath = this.selectedPath().slice(0, index);
    this.selectedPath.set(newPath);
    this.selectionChange.emit(newPath.at(-1) ?? null);
  }

  expand(node: FilterNode): void {
    this.navStack.update((stack) => [...stack, node]);
    this.searchQuery.set('');
    setTimeout(() => this.autoTrigger().openPanel(), 0);
  }

  goBack(): void {
    this.navStack.update((stack) => stack.slice(0, -1));
    this.searchQuery.set('');
    setTimeout(() => this.autoTrigger().openPanel(), 0);
  }

  // Vor dem Panel-Öffnen: Nav-Level aus selectedPath wiederherstellen.
  // Muss auf (focus) passieren – nicht auf (opened) – damit Angular Material
  // beim Öffnen bereits die korrekten Options vorfindet und keine Re-Render
  // während des Öffnens den internen Options-Snapshot ungültig macht.
  onInputFocus(): void {
    if (this.navStack().length === 0 && this.selectedPath().length > 1) {
      this.navStack.set(this.selectedPath().slice(0, -1));
    }
  }

  onSearchInput(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }

  onPanelOpened(): void {
    // Zur selektierten Option scrollen – innerhalb dieses Panels (nicht global)
    setTimeout(() => {
      const panel = this.autoTrigger().autocomplete.panel?.nativeElement as HTMLElement | undefined;
      panel
        ?.querySelector('.check-icon.visible')
        ?.closest('mat-option')
        ?.scrollIntoView({ block: 'nearest' });
    }, 0);
  }

  handleBlur(): void {
    // Wenn das Panel noch offen ist, wurde der Blur durch einen Option-Klick
    // ausgelöst. In dem Fall nichts löschen – onSelect() räumt danach auf.
    if (this.autoTrigger().panelOpen) return;
    this.searchQuery.set('');
    this.navStack.set([]);
  }

  clear(): void {
    this.selectedPath.set([]);
    this.navStack.set([]);
    this.searchQuery.set('');
    this.selectionChange.emit(null);
  }
}
