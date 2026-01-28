"use client";

import { format } from "date-fns";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { type TrackingLookupResponse } from "@/lib/api/tracking";
import {
  SHIPMENT_STATUS_LABELS,
  SHIPMENT_STATUS_VARIANTS,
} from "@/types/api";

interface TrackingLookupResultsTableProps {
  trackings: TrackingLookupResponse[];
}

export function TrackingLookupResultsTable({
  trackings,
}: TrackingLookupResultsTableProps) {
  if (trackings.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        No tracking information yet. Enter a tracking number above to get
        started.
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Tracking Number</TableHead>
          <TableHead>Carrier</TableHead>
          <TableHead>Order Status</TableHead>
          <TableHead>Date Ordered</TableHead>
          <TableHead>Expected Delivery</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {trackings.map((tracking) => (
          <TableRow key={tracking.trackingNumber}>
            <TableCell className="font-mono">
              {tracking.trackingNumber}
            </TableCell>
            <TableCell className="capitalize">{tracking.carrier}</TableCell>
            <TableCell>
              <Badge variant={SHIPMENT_STATUS_VARIANTS[tracking.orderStatus]}>
                {SHIPMENT_STATUS_LABELS[tracking.orderStatus]}
              </Badge>
            </TableCell>
            <TableCell>
              {tracking.dateOrdered
                ? format(new Date(tracking.dateOrdered), "MMM d, yyyy")
                : "—"}
            </TableCell>
            <TableCell>
              {tracking.expectedDelivery
                ? format(new Date(tracking.expectedDelivery), "MMM d, yyyy")
                : "—"}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
