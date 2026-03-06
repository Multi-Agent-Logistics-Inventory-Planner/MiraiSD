"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Image from "next/image";
import { ArrowLeft, ImageOff, Plus, Pencil } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import { ProductForm } from "@/components/products/product-form";
import { PrizeForm } from "@/components/products/prize-form";
import {
  useProductWithChildren,
  useProduct,
} from "@/hooks/queries/use-products";
import { cn } from "@/lib/utils";
import type { ProductSummary } from "@/types/api";

function getProductStatusColor(isActive: boolean) {
  return isActive
    ? "bg-[#20d760] text-black"
    : "bg-[#e50815] text-white";
}

function PrizeSkeleton() {
  return (
    <>
      {Array.from({ length: 5 }).map((_, i) => (
        <TableRow key={i}>
          <TableCell className="py-2">
            <div className="flex items-center gap-3">
              <Skeleton className="h-10 w-10 rounded-md" />
              <Skeleton className="h-4 w-32" />
            </div>
          </TableCell>
          <TableCell>
            <Skeleton className="h-4 w-20" />
          </TableCell>
          <TableCell className="text-center">
            <Skeleton className="h-6 w-16 mx-auto" />
          </TableCell>
          <TableCell className="text-right">
            <Skeleton className="h-4 w-8 ml-auto" />
          </TableCell>
        </TableRow>
      ))}
    </>
  );
}

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const productId = params.id as string;

  const { data: product, isLoading, error } = useProductWithChildren(productId);

  const [addPrizeOpen, setAddPrizeOpen] = useState(false);
  const [editParentOpen, setEditParentOpen] = useState(false);
  const [editingPrizeId, setEditingPrizeId] = useState<string | null>(null);

  const { data: editingPrizeProduct, isLoading: editingPrizeLoading } =
    useProduct(editingPrizeId);

  if (isLoading) {
    return (
      <div className="flex flex-col p-4 md:p-8 space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-10 w-10" />
          <div className="space-y-2">
            <Skeleton className="h-6 w-48" />
            <Skeleton className="h-4 w-32" />
          </div>
        </div>
        <Card>
          <CardHeader>
            <Skeleton className="h-6 w-24" />
          </CardHeader>
          <CardContent>
            <PrizeSkeleton />
          </CardContent>
        </Card>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="flex flex-col p-4 md:p-8 space-y-6">
        <Button
          variant="ghost"
          size="sm"
          className="w-fit"
          onClick={() => router.push("/products")}
        >
          <ArrowLeft className="h-4 w-4 mr-2" />
          Back to Products
        </Button>
        <Card>
          <CardHeader>
            <CardTitle>Product Not Found</CardTitle>
          </CardHeader>
          <CardContent className="text-muted-foreground">
            {error instanceof Error ? error.message : "The product could not be found."}
          </CardContent>
        </Card>
      </div>
    );
  }

  const children = product.children ?? [];
  const totalStock = product.totalChildStock ?? 0;

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-6">
      {/* Header */}
      <div className="flex items-start gap-4">
        <Button
          variant="ghost"
          size="icon"
          className="shrink-0 mt-1"
          onClick={() => router.push("/products")}
        >
          <ArrowLeft className="h-5 w-5" />
        </Button>

        <div className="flex items-start gap-4 flex-1 min-w-0">
          {product.imageUrl ? (
            <div className="relative h-16 w-16 sm:h-20 sm:w-20 shrink-0 overflow-hidden rounded-lg bg-muted">
              <Image
                src={product.imageUrl}
                alt={product.name}
                fill
                sizes="80px"
                className="object-cover"
              />
            </div>
          ) : (
            <div className="flex h-16 w-16 sm:h-20 sm:w-20 shrink-0 items-center justify-center rounded-lg bg-muted">
              <ImageOff className="h-6 w-6 text-muted-foreground" />
            </div>
          )}

          <div className="min-w-0 flex-1 space-y-1">
            <h1 className="text-xl sm:text-2xl font-semibold truncate">
              {product.name}
            </h1>
            <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              {product.sku && (
                <Badge variant="outline" className="font-mono">
                  {product.sku}
                </Badge>
              )}
              <Badge variant="secondary">{product.category.name}</Badge>
              <Badge className={cn(getProductStatusColor(product.isActive))}>
                {product.isActive ? "Active" : "Inactive"}
              </Badge>
              <Button
                variant="outline"
                size="sm"
                className="h-7 gap-1.5"
                onClick={() => setEditParentOpen(true)}
              >
                <Pencil className="h-3.5 w-3.5" />
                Edit
              </Button>
            </div>
            <p className="text-sm text-muted-foreground">
              Total Stock: <span className="font-medium text-foreground">{totalStock}</span>
              {" | "}
              Prizes: <span className="font-medium text-foreground">{children.length}</span>
            </p>
          </div>
        </div>
      </div>

      {/* Prizes Table */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Prizes</CardTitle>
          <Button
            size="sm"
            onClick={() => setAddPrizeOpen(true)}
            className="text-white bg-[#0b66c2] hover:bg-[#0a5eb3] dark:bg-[#7c3aed] dark:hover:bg-[#6d28d9] dark:text-foreground"
          >
            <Plus className="h-4 w-4 mr-2" />
            Add Prize
          </Button>
        </CardHeader>
        <CardContent>
          {children.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              No prizes added yet. Click &quot;Add Prize&quot; to create the first prize.
            </div>
          ) : (
            <Table>
              <DataTableHeader>
                <TableHead className="rounded-l-lg w-12">Letter</TableHead>
                <TableHead>Prize</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead className="text-center w-24">Status</TableHead>
                <TableHead className="text-right w-20 rounded-r-lg">Stock</TableHead>
                <TableHead className="w-10 rounded-r-lg"></TableHead>
              </DataTableHeader>
              <TableBody>
                {children.map((prize) => (
                  <TableRow
                    key={prize.id}
                    className="cursor-pointer hover:bg-muted/50"
                    onClick={() => setEditingPrizeId(prize.id)}
                    title="Click to edit prize"
                  >
                    <TableCell className="py-2 w-12 font-mono font-medium">
                      {prize.letter ?? "-"}
                    </TableCell>
                    <TableCell className="py-2">
                      <div className="flex items-center gap-3">
                        {prize.imageUrl ? (
                          <div className="relative h-10 w-10 shrink-0 overflow-hidden rounded-md bg-muted">
                            <Image
                              src={prize.imageUrl}
                              alt={prize.name}
                              fill
                              sizes="40px"
                              className="object-cover"
                            />
                          </div>
                        ) : (
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-muted">
                            <ImageOff className="h-4 w-4 text-muted-foreground" />
                          </div>
                        )}
                        <span className="font-medium truncate">{prize.name}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground font-mono text-sm">
                      {prize.sku ?? "-"}
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge className={cn("text-xs", getProductStatusColor(prize.isActive))}>
                        {prize.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {prize.quantity}
                    </TableCell>
                    <TableCell className="w-10">
                      <Pencil className="h-3.5 w-3.5 text-muted-foreground" />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* Add Prize Dialog */}
      <PrizeForm
        open={addPrizeOpen}
        onOpenChange={setAddPrizeOpen}
        parentId={product.id}
        parentName={product.name}
        parentCategoryId={product.category.id}
      />

      {/* Edit Parent Dialog */}
      <ProductForm
        open={editParentOpen}
        onOpenChange={setEditParentOpen}
        initialProduct={product}
      />

      {/* Edit Prize Dialog - only open when product is loaded */}
      <ProductForm
        open={!!editingPrizeId && !!editingPrizeProduct && !editingPrizeLoading}
        onOpenChange={(open) => !open && setEditingPrizeId(null)}
        initialProduct={editingPrizeProduct ?? null}
      />
    </div>
  );
}
