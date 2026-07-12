"use client";

import * as React from "react";
import { Slider } from "@base-ui/react/slider";
import { cn } from "@/lib/utils";

interface BaseSliderProps {
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onValueChange: (value: number) => void;
  disabled?: boolean;
  className?: string;
}

function BaseSlider({
  value,
  min = 0,
  max = 1,
  step = 0.01,
  onValueChange,
  disabled = false,
  className,
}: BaseSliderProps) {
  return (
    <Slider.Root
      value={[value]}
      onValueChange={(newValue) => onValueChange(newValue[0])}
      min={min}
      max={max}
      step={step}
      disabled={disabled}
      className={cn(
        "relative flex w-full touch-none select-none items-center",
        className,
      )}
    >
      <Slider.Control className="relative flex w-full items-center">
        <Slider.Track className="relative h-2 w-full grow rounded-full bg-secondary">
          <Slider.Indicator className="absolute h-full rounded-full bg-primary" />
        </Slider.Track>
        <Slider.Thumb className="block size-5 rounded-full border-2 border-primary bg-background ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50" />
      </Slider.Control>
    </Slider.Root>
  );
}

export { BaseSlider };
