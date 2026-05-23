"use client";

import { Clock } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

interface TimePickerButtonProps {
  /** 24-hour HH:MM string, e.g. "23:45". Empty string clears the value. */
  readonly value: string;
  readonly onChange: (next: string) => void;
  readonly id?: string;
  readonly placeholder?: string;
}

/**
 * Time picker that matches the audit-log calendar trigger: a Button-styled
 * Popover trigger with the value inside, opening to a panel with hour + minute
 * + AM/PM Selects. Values are stored as 24-hour "HH:MM".
 */
export function TimePickerButton({
  value,
  onChange,
  id,
  placeholder = "--:--",
}: TimePickerButtonProps) {
  const parsed = parse24h(value);
  const display = parsed ? format12h(parsed.hour24, parsed.minute) : null;

  const hour12 = parsed ? to12(parsed.hour24).hour12 : 12;
  const period: "AM" | "PM" = parsed
    ? to12(parsed.hour24).period
    : "AM";
  const minute = parsed ? parsed.minute : 0;

  const writeBack = (h12: number, m: number, p: "AM" | "PM") => {
    onChange(to24h(h12, m, p));
  };

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          id={id}
          variant="outline"
          className={cn(
            "w-full justify-start text-left font-normal dark:bg-input dark:border-[#41413d]",
            !display && "text-muted-foreground"
          )}
        >
          {display ?? <span>{placeholder}</span>}
          <Clock className="ml-auto h-4 w-4 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-3" align="start">
        <div className="grid grid-cols-3 gap-2">
          <Select
            value={String(hour12)}
            onValueChange={(v) => writeBack(Number(v), minute, period)}
          >
            <SelectTrigger className="w-[78px] font-mono tabular-nums">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Array.from({ length: 12 }, (_, i) => i + 1).map((h) => (
                <SelectItem key={h} value={String(h)} className="font-mono">
                  {String(h).padStart(2, "0")}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={String(minute)}
            onValueChange={(v) => writeBack(hour12, Number(v), period)}
          >
            <SelectTrigger className="w-[78px] font-mono tabular-nums">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Array.from({ length: 60 }, (_, i) => i).map((m) => (
                <SelectItem key={m} value={String(m)} className="font-mono">
                  {String(m).padStart(2, "0")}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={period}
            onValueChange={(v) => writeBack(hour12, minute, v as "AM" | "PM")}
          >
            <SelectTrigger className="w-[78px] font-mono">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="AM" className="font-mono">
                AM
              </SelectItem>
              <SelectItem value="PM" className="font-mono">
                PM
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </PopoverContent>
    </Popover>
  );
}

function parse24h(value: string): { hour24: number; minute: number } | null {
  if (!value) return null;
  const [hStr, mStr] = value.split(":");
  const h = Number.parseInt(hStr, 10);
  const m = Number.parseInt(mStr, 10);
  if (!Number.isFinite(h) || !Number.isFinite(m)) return null;
  if (h < 0 || h > 23 || m < 0 || m > 59) return null;
  return { hour24: h, minute: m };
}

function to12(hour24: number): { hour12: number; period: "AM" | "PM" } {
  const period: "AM" | "PM" = hour24 >= 12 ? "PM" : "AM";
  const hour12 = hour24 % 12 === 0 ? 12 : hour24 % 12;
  return { hour12, period };
}

function to24h(hour12: number, minute: number, period: "AM" | "PM"): string {
  let h = hour12 % 12;
  if (period === "PM") h += 12;
  return `${String(h).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function format12h(hour24: number, minute: number): string {
  const { hour12, period } = to12(hour24);
  return `${String(hour12).padStart(2, "0")}:${String(minute).padStart(2, "0")} ${period}`;
}
