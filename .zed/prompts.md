# Frontend Styling Prompts

## Icon Sizing

**Rule:** Do NOT set default Material icon sizing. Only set explicit `font-size`, `width`, `height` when design specifically requires it.

### When to size icons:

1. **Table alignment** — Control cell spacing
   ```scss
   .col-icon mat-icon {
     font-size: 20px;
     width: 20px;
     height: 20px;
     display: block;
   }
   ```

2. **Visual hierarchy** — Prominent headers need larger sizes (24px+)
   ```scss
   .header-icon {
     font-size: 28px;
     width: 28px;
     height: 28px;
     flex-shrink: 0;
   }
   ```

3. **Dense layouts** — Compact UI smaller than Material default (18px)

### Default pattern (no sizing):

```scss
mat-icon {
  color: var(--mat-sys-primary);
  flex-shrink: 0;  // Prevent truncation in flex
}

button mat-icon {
  color: inherit;
}
```

### Why:
- Material default (18px) works for 90% of cases
- Explicit sizing is layout-driven, not styling-driven
- `flex-shrink: 0` prevents truncation in flex containers
- Reduces CSS maintenance burden

## Button Icon Colors

Use the `color: inherit;` pattern to let icons inherit button color:

```scss
button mat-icon {
  color: inherit;
}

.edit-btn {
  color: var(--mat-sys-primary);
}
```

Apply the class to the button, not the icon.

## References
- Icon sizing guide: `memory/frontend_icon_sizing.md`
- Icon color inheritance: `memory/feedback_icon_colors_in_buttons.md`
