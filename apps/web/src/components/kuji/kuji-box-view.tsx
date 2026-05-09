"use client";

import { useState } from "react";
import { ListTodo, Loader2, Lock, MinusCircle, PackageOpen, Undo2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useActiveKujiBox } from "@/hooks/queries/use-kuji-box";
import { usePermissions } from "@/hooks/use-permissions";
import { UserRole, KujiBoxStatus } from "@/types/api";
import type { KujiBox, KujiBoxTier } from "@/types/api";
import { ManageTiersDialog } from "./manage-tiers-dialog";
import { OpenBoxDialog } from "./open-box-dialog";
import { CloseBoxDialog } from "./close-box-dialog";
import { ReopenBoxDialog } from "./reopen-box-dialog";
import { RecordDrawDialog } from "./record-draw-dialog";
import { UndoDrawDialog } from "./undo-draw-dialog";
import { TierEditDialog } from "./tier-edit-dialog";
import { AddSlipDialog } from "./add-slip-dialog";
import { TransferInDialog } from "./transfer-in-dialog";
import { KujiHistoryView } from "./kuji-history-view";

interface KujiBoxViewProps {
  readonly open: boolean;
  readonly onOpenChange: (open: boolean) => void;
  readonly productId: string;
  readonly productName: string;
}

export function KujiBoxView({
  open,
  onOpenChange,
  productId,
  productName,
}: KujiBoxViewProps) {
  const activeQuery = useActiveKujiBox(open ? productId : null);
  const { role } = usePermissions();

  const canStructural =
    role === UserRole.ADMIN || role === UserRole.ASSISTANT_MANAGER;
  const canDraw =
    role === UserRole.ADMIN ||
    role === UserRole.ASSISTANT_MANAGER ||
    role === UserRole.EMPLOYEE;

  const [openBoxOpen, setOpenBoxOpen] = useState(false);
  const [closeBoxOpen, setCloseBoxOpen] = useState(false);
  const [reopenBoxOpen, setReopenBoxOpen] = useState(false);
  const [recordDrawOpen, setRecordDrawOpen] = useState(false);
  const [undoDrawOpen, setUndoDrawOpen] = useState(false);
  const [manageTiersOpen, setManageTiersOpen] = useState(false);
  const [editTierOpen, setEditTierOpen] = useState(false);
  const [addSlipOpen, setAddSlipOpen] = useState(false);
  const [transferInOpen, setTransferInOpen] = useState(false);
  const [activeTier, setActiveTier] = useState<KujiBoxTier | null>(null);

  const box: KujiBox | null = activeQuery.data ?? null;
  const boxIsOpen = box?.status === KujiBoxStatus.OPEN;

  function handleEditTier(tier: KujiBoxTier) {
    setActiveTier(tier);
    setEditTierOpen(true);
  }
  function handleAddSlip(tier: KujiBoxTier) {
    setActiveTier(tier);
    setAddSlipOpen(true);
  }
  function handleTransferIn(tier: KujiBoxTier) {
    setActiveTier(tier);
    setTransferInOpen(true);
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="w-[calc(100%-2rem)] sm:max-w-3xl max-h-[90vh] flex flex-col gap-0 p-0 overflow-hidden">
          <DialogHeader className="p-6 pb-2">
            <DialogTitle className="flex items-center gap-2">
              <span className="truncate">{productName}</span>
              {box ? (
                <Badge
                  variant={boxIsOpen ? "default" : "secondary"}
                  className="text-xs"
                >
                  {box.status}
                </Badge>
              ) : null}
            </DialogTitle>
            <DialogDescription>Manage the active kuji box and view history.</DialogDescription>
          </DialogHeader>

          <Tabs defaultValue="active" className="flex-1 flex flex-col min-h-0">
            <div className="px-6 shrink-0">
              <TabsList>
                <TabsTrigger value="active">Active Box</TabsTrigger>
                <TabsTrigger value="history">History</TabsTrigger>
              </TabsList>
            </div>

            <TabsContent
              value="active"
              className="flex-1 overflow-y-auto px-6 pb-4 mt-3"
            >
              {activeQuery.isLoading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : !box ? (
                <div className="text-center py-12 space-y-3">
                  <PackageOpen className="h-10 w-10 text-muted-foreground/40 mx-auto" />
                  <p className="text-sm text-muted-foreground">No box open.</p>
                  {canStructural ? (
                    <Button
                      type="button"
                      onClick={() => setOpenBoxOpen(true)}
                    >
                      Open Box
                    </Button>
                  ) : null}
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="space-y-1 text-sm">
                      <div>
                        <span className="text-muted-foreground">Location:</span>{" "}
                        <span className="font-mono">
                          {box.locationCode ?? box.locationName ?? "—"}
                        </span>
                      </div>
                      <div>
                        <span className="text-muted-foreground">Total slips:</span>{" "}
                        <span className="tabular-nums">
                          {box.totalCount.toLocaleString()}
                        </span>
                      </div>
                      {box.label ? (
                        <div>
                          <span className="text-muted-foreground">Label:</span>{" "}
                          {box.label}
                        </div>
                      ) : null}
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {boxIsOpen && canDraw ? (
                        <Button
                          type="button"
                          size="sm"
                          onClick={() => setRecordDrawOpen(true)}
                        >
                          Record Draw
                        </Button>
                      ) : null}
                      {boxIsOpen && canDraw ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setUndoDrawOpen(true)}
                        >
                          <Undo2 className="h-4 w-4 mr-1" />
                          Undo Draw
                        </Button>
                      ) : null}
                      {boxIsOpen && canStructural ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setManageTiersOpen(true)}
                        >
                          <ListTodo className="h-4 w-4 mr-1" />
                          Manage Tiers
                        </Button>
                      ) : null}
                      {boxIsOpen && canStructural ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setCloseBoxOpen(true)}
                        >
                          <Lock className="h-4 w-4 mr-1" />
                          Close Box
                        </Button>
                      ) : null}
                      {!boxIsOpen && canStructural ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => setReopenBoxOpen(true)}
                        >
                          <MinusCircle className="h-4 w-4 mr-1" />
                          Reopen
                        </Button>
                      ) : null}
                    </div>
                  </div>

                </div>
              )}
            </TabsContent>

            <TabsContent
              value="history"
              className="flex-1 overflow-y-auto px-6 pb-4 mt-3"
            >
              <KujiHistoryView productId={productId} />
            </TabsContent>
          </Tabs>
        </DialogContent>
      </Dialog>

      {/* Child dialogs */}
      <OpenBoxDialog
        open={openBoxOpen}
        onOpenChange={setOpenBoxOpen}
        productId={productId}
        productName={productName}
      />

      {box ? (
        <>
          <CloseBoxDialog
            open={closeBoxOpen}
            onOpenChange={setCloseBoxOpen}
            box={box}
          />
          <ReopenBoxDialog
            open={reopenBoxOpen}
            onOpenChange={setReopenBoxOpen}
            box={box}
          />
          <RecordDrawDialog
            open={recordDrawOpen}
            onOpenChange={setRecordDrawOpen}
            box={box}
          />
          <UndoDrawDialog
            open={undoDrawOpen}
            onOpenChange={setUndoDrawOpen}
            box={box}
          />
          <ManageTiersDialog
            open={manageTiersOpen}
            onOpenChange={setManageTiersOpen}
            box={box}
            canEditStructural={canStructural}
            onEditTier={handleEditTier}
            onAddSlip={handleAddSlip}
            onTransferIn={handleTransferIn}
          />
          {activeTier ? (
            <>
              <TierEditDialog
                open={editTierOpen}
                onOpenChange={(o) => {
                  setEditTierOpen(o);
                  if (!o) setActiveTier(null);
                }}
                box={box}
                tier={activeTier}
              />
              <AddSlipDialog
                open={addSlipOpen}
                onOpenChange={(o) => {
                  setAddSlipOpen(o);
                  if (!o) setActiveTier(null);
                }}
                box={box}
                tier={activeTier}
              />
              <TransferInDialog
                open={transferInOpen}
                onOpenChange={(o) => {
                  setTransferInOpen(o);
                  if (!o) setActiveTier(null);
                }}
                box={box}
                tier={activeTier}
              />
            </>
          ) : null}
        </>
      ) : null}
    </>
  );
}
