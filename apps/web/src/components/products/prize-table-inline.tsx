"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { prizeLetterDisplay, sortPrizes } from "@/lib/utils";

export interface PendingPrize {
  tempId: string;
  letter: string;
  templateQuantity: number;
  quantity: number;
}

interface PrizeTableInlineProps {
  prizes: PendingPrize[];
  onAddPrize: (prize: Omit<PendingPrize, "tempId">) => void;
  onDeletePrize: (tempId: string) => void;
  disabled?: boolean;
}

export function PrizeTableInline({
  prizes,
  onAddPrize,
  onDeletePrize,
  disabled = false,
}: PrizeTableInlineProps) {
  const [letter, setLetter] = useState("");
  const [templateQuantity, setTemplateQuantity] = useState("");
  const [quantity, setQuantity] = useState("");
  const [error, setError] = useState("");

  const sortedPrizes = sortPrizes(prizes);

  function handleAdd() {
    const trimmedLetter = letter.trim();
    if (!trimmedLetter) {
      setError("Letter is required");
      return;
    }
    if (trimmedLetter.length > 50) {
      setError("Max 50 characters");
      return;
    }

    // Check for duplicate letter
    const normalizedLetter = prizeLetterDisplay(trimmedLetter);
    const isDuplicate = prizes.some(
      (p) => prizeLetterDisplay(p.letter) === normalizedLetter
    );
    if (isDuplicate) {
      setError("This letter already exists");
      return;
    }

    const tq = templateQuantity.trim();
    const qty = quantity.trim();

    onAddPrize({
      letter: trimmedLetter,
      templateQuantity: tq === "" ? 0 : parseInt(tq, 10),
      quantity: qty === "" ? 0 : parseInt(qty, 10),
    });

    // Reset form
    setLetter("");
    setTemplateQuantity("");
    setQuantity("");
    setError("");
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter") {
      e.preventDefault();
      handleAdd();
    }
  }

  return (
    <div className="space-y-3">
      {sortedPrizes.length === 0 ? (
        <div className="text-center py-4 text-muted-foreground text-sm">
          No prizes added yet.
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-24">Letter</TableHead>
              <TableHead className="text-right w-20">Qty/Set</TableHead>
              <TableHead className="text-right">Pieces</TableHead>
              <TableHead className="w-12"></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {sortedPrizes.map((prize) => (
              <TableRow key={prize.tempId}>
                <TableCell className="font-mono font-medium">
                  {prizeLetterDisplay(prize.letter) || "-"}
                </TableCell>
                <TableCell className="text-right text-muted-foreground">
                  {prize.templateQuantity ?? "-"}
                </TableCell>
                <TableCell className="text-right">
                  {prize.quantity?.toLocaleString() ?? "-"}
                </TableCell>
                <TableCell>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="h-8 w-8"
                    onClick={() => onDeletePrize(prize.tempId)}
                    disabled={disabled}
                  >
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* Inline add form */}
      <div className="flex gap-2 items-start">
        <div className="flex-1 grid grid-cols-3 gap-2">
          <div>
            <Input
              placeholder="Letter"
              value={letter}
              onChange={(e) => {
                setLetter(e.target.value);
                setError("");
              }}
              onKeyDown={handleKeyDown}
              maxLength={50}
              className="font-mono"
              disabled={disabled}
            />
          </div>
          <div>
            <Input
              type="number"
              placeholder="Qty/Set"
              value={templateQuantity}
              onChange={(e) => setTemplateQuantity(e.target.value)}
              onKeyDown={handleKeyDown}
              min={0}
              disabled={disabled}
            />
          </div>
          <div>
            <Input
              type="number"
              placeholder="Pieces"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              onKeyDown={handleKeyDown}
              min={0}
              disabled={disabled}
            />
          </div>
        </div>
        <Button
          type="button"
          variant="outline"
          size="icon"
          onClick={handleAdd}
          disabled={disabled}
        >
          <Plus className="h-4 w-4" />
        </Button>
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  );
}
