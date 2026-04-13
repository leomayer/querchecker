# Frontend Styling Guidelines

## Icon Sizing

### Principle
Do NOT set explicit icon sizing by default. Let Material icons use their natural 18px size. Only override when layout or design hierarchy requires it.

### When to Set Sizing

#### 1. Table Layouts (20px)
Icons in table cells need controlled sizing for alignment:

```scss
.col-icon mat-icon {
  font-size: 20px;
  width: 20px;
  height: 20px;
  display: block;
}

.col-check mat-icon {
  font-size: 20px;
  width: 20px;
  height: 20px;
  display: block;
  margin: 0 auto;
}
```

#### 2. Visual Hierarchy (24px–28px+)
Prominent icons in headers or primary UI elements can be larger to establish hierarchy:

```scss
.header-icon {
  font-size: 28px;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
}
```

#### 3. Dense Layouts
When space is constrained, icons may be sized smaller (but rarely needed):

```scss
.compact-icon {
  font-size: 16px;
  width: 16px;
  height: 16px;
}
```

### Default Pattern (No Sizing)

```scss
mat-icon {
  color: var(--mat-sys-primary);
  flex-shrink: 0;  // Prevents truncation in flex containers
}
```

**Key:** The `flex-shrink: 0` is more important than sizing. It prevents icons from being squeezed in flex layouts.

### Button Icons

Use color inheritance instead of sizing:

```scss
button mat-icon {
  color: inherit;
}

.primary-btn {
  color: var(--mat-sys-primary);
}
```

Apply color to the button, not the icon.

## Reasoning

1. **Material Default (18px) is intentional** — Designed to work across most UIs
2. **Explicit sizing adds burden** — More CSS to maintain, increased file size
3. **Layout needs drive sizing** — Sizing should be a layout decision, not a style choice
4. **Flex-shrink prevents truncation** — More reliable than sizing for preventing issues
5. **Consistency across components** — Reduces visual surprises

## Code Examples

### ✅ Good: Button with inherited color, no sizing
```scss
button mat-icon {
  color: inherit;
}

.edit-btn {
  color: var(--mat-sys-primary);
}
```

```html
<button class="edit-btn" mat-icon-button>
  <mat-icon>edit</mat-icon>
</button>
```

### ✅ Good: Table icon with sizing
```scss
.col-icon mat-icon {
  font-size: 20px;
  width: 20px;
  height: 20px;
  display: block;
}
```

### ❌ Avoid: Unnecessary sizing on default icons
```scss
/* Don't do this — Material default works fine */
mat-icon {
  font-size: 18px;
  width: 18px;
  height: 18px;
}
```

### ❌ Avoid: Sizing in flex layouts that don't need it
```scss
/* Use flex-shrink instead */
.banner mat-icon {
  font-size: 18px;  /* Redundant */
  width: 18px;      /* Redundant */
  height: 18px;     /* Redundant */
}

/* Better: */
.banner mat-icon {
  flex-shrink: 0;
}
```

## Related Guidelines

- [Icon Color Inheritance](./frontend-icon-colors.md) — How to set button icon colors
- [Material Design 3](https://m3.material.io/) — Official sizing guidelines
