export interface HorizontalLine {
  id: string;
  price: number;
  color: string;
  label?: string;
  lineWidth?: number;
  lineStyle?: "solid" | "dashed" | "dotted";
}

export interface PriceAnnotation {
  id: string;
  price: number;
  time: number;
  text: string;
  color: string;
}

let lineIdCounter = 0;

export function createHorizontalLine(price: number, color = "#f0b429", label?: string): HorizontalLine {
  return {
    id: `hline-${++lineIdCounter}`,
    price,
    color,
    label,
    lineWidth: 1,
    lineStyle: "dashed",
  };
}

export function createPriceAnnotation(price: number, time: number, text: string, color = "#26a69a"): PriceAnnotation {
  return {
    id: `annot-${++lineIdCounter}`,
    price,
    time,
    text,
    color,
  };
}

export function removeDrawing(drawings: (HorizontalLine | PriceAnnotation)[], id: string): (HorizontalLine | PriceAnnotation)[] {
  return drawings.filter(d => d.id !== id);
}

export function loadDrawings(key: string): (HorizontalLine | PriceAnnotation)[] {
  try {
    const saved = localStorage.getItem(`tj_drawings_${key}`);
    return saved ? JSON.parse(saved) : [];
  } catch { return []; }
}

export function saveDrawings(key: string, drawings: (HorizontalLine | PriceAnnotation)[]): void {
  localStorage.setItem(`tj_drawings_${key}`, JSON.stringify(drawings));
}
