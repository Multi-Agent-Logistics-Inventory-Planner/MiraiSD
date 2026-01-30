"use client";

import { useState } from "react";
import { Search } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import {
  lookupTracking,
  type TrackingLookupResponse,
} from "@/lib/api/tracking";

interface TrackingLookupCardProps {
  onTrackingAdded: (tracking: TrackingLookupResponse) => void;
}

export function TrackingLookupCard({
  onTrackingAdded,
}: TrackingLookupCardProps) {
  const { toast } = useToast();
  const [trackingNumber, setTrackingNumber] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  async function handleLookup() {
    if (!trackingNumber.trim()) {
      toast({
        title: "Error",
        description: "Please enter a tracking number",
        variant: "destructive",
      });
      return;
    }

    setIsLoading(true);
    try {
      const result = await lookupTracking({
        trackingNumber: trackingNumber.trim(),
      });
      onTrackingAdded(result);
      setTrackingNumber("");
      toast({
        title: "Tracking added",
        description: `Carrier: ${result.carrier}`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description:
          error instanceof Error
            ? error.message
            : "Failed to lookup tracking",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Track Shipment</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex gap-2">
          <Input
            placeholder="Enter tracking number..."
            value={trackingNumber}
            onChange={(e) => setTrackingNumber(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleLookup()}
            disabled={isLoading}
          />
          <Button onClick={handleLookup} disabled={isLoading}>
            <Search className="mr-2 h-4 w-4" />
            {isLoading ? "Tracking..." : "Track"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
