"use client";

import { useState, useEffect, KeyboardEvent } from "react";
import { Loader2, Plus, Pencil, Trash2, X, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  getReviewEmployees,
  createReviewEmployee,
  updateReviewEmployee,
} from "@/lib/api/reviews";
import { ReviewEmployee } from "@/types/api";

const ITEMS_PER_PAGE = 5;

interface TagInputProps {
  tags: string[];
  onChange: (tags: string[]) => void;
  disabled?: boolean;
  placeholder?: string;
}

function TagInput({ tags, onChange, disabled, placeholder }: TagInputProps) {
  const [inputValue, setInputValue] = useState("");

  const addTag = () => {
    const trimmed = inputValue.trim();
    if (trimmed && !tags.includes(trimmed)) {
      onChange([...tags, trimmed]);
      setInputValue("");
    }
  };

  const removeTag = (tagToRemove: string) => {
    onChange(tags.filter((tag) => tag !== tagToRemove));
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      addTag();
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <Input
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled}
          className="flex-1"
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={addTag}
          disabled={disabled || !inputValue.trim()}
        >
          Add
        </Button>
      </div>
      {tags.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {tags.map((tag) => (
            <Badge key={tag} variant="secondary" className="pl-2 pr-1 py-1">
              {tag}
              <button
                type="button"
                onClick={() => removeTag(tag)}
                disabled={disabled}
                className="ml-1 hover:text-destructive"
              >
                <X className="h-3 w-3" />
              </button>
            </Badge>
          ))}
        </div>
      )}
    </div>
  );
}

interface EditEmployeeModalProps {
  employee: ReviewEmployee | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function EditEmployeeModal({
  employee,
  open,
  onOpenChange,
  onSuccess,
}: EditEmployeeModalProps) {
  const [name, setName] = useState("");
  const [aliases, setAliases] = useState<string[]>([]);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (employee) {
      setName(employee.canonicalName);
      setAliases(employee.nameVariants || []);
    }
  }, [employee]);

  const handleSave = async () => {
    if (!employee || !name.trim()) return;

    setIsSaving(true);
    try {
      await updateReviewEmployee(employee.id, {
        canonicalName: name.trim(),
        nameVariants: aliases,
      });
      onSuccess();
      onOpenChange(false);
    } catch (error) {
      // Error handled silently
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Edit Employee</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="space-y-2">
            <Label htmlFor="edit-name">Name</Label>
            <Input
              id="edit-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={isSaving}
            />
          </div>
          <div className="space-y-2">
            <Label>Aliases</Label>
            <TagInput
              tags={aliases}
              onChange={setAliases}
              disabled={isSaving}
              placeholder="Add an alias..."
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSaving}
            >
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={isSaving || !name.trim()}>
              {isSaving && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
              Save
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface AddEmployeeModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function AddEmployeeModal({
  open,
  onOpenChange,
  onSuccess,
}: AddEmployeeModalProps) {
  const [name, setName] = useState("");
  const [aliases, setAliases] = useState<string[]>([]);
  const [isSaving, setIsSaving] = useState(false);

  const resetForm = () => {
    setName("");
    setAliases([]);
  };

  const handleOpenChange = (isOpen: boolean) => {
    if (!isOpen) resetForm();
    onOpenChange(isOpen);
  };

  const handleSave = async () => {
    if (!name.trim()) return;

    setIsSaving(true);
    try {
      await createReviewEmployee({
        canonicalName: name.trim(),
        nameVariants: aliases,
      });
      onSuccess();
      handleOpenChange(false);
    } catch (error) {
      // Error handled silently
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Add Employee</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="space-y-2">
            <Label htmlFor="add-name">Name</Label>
            <Input
              id="add-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Employee name"
              disabled={isSaving}
            />
          </div>
          <div className="space-y-2">
            <Label>Aliases</Label>
            <TagInput
              tags={aliases}
              onChange={setAliases}
              disabled={isSaving}
              placeholder="Add an alias..."
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={isSaving}
            >
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={isSaving || !name.trim()}>
              {isSaving && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
              Add
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface ManageEmployeesDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

export function ManageEmployeesDialog({
  open,
  onOpenChange,
  onSuccess,
}: ManageEmployeesDialogProps) {
  const [employees, setEmployees] = useState<ReviewEmployee[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [deleteTarget, setDeleteTarget] = useState<ReviewEmployee | null>(null);
  const [editTarget, setEditTarget] = useState<ReviewEmployee | null>(null);
  const [isAddOpen, setIsAddOpen] = useState(false);

  const totalPages = Math.ceil(employees.length / ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginatedEmployees = employees.slice(startIndex, startIndex + ITEMS_PER_PAGE);

  const fetchEmployees = async () => {
    setIsLoading(true);
    try {
      const data = await getReviewEmployees();
      setEmployees(data);
    } catch (error) {
      // Error handled silently
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      fetchEmployees();
      setCurrentPage(1);
    }
  }, [open]);

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await updateReviewEmployee(deleteTarget.id, { isActive: false });
      setDeleteTarget(null);
      fetchEmployees();
      onSuccess();
    } catch (error) {
      // Error handled silently
    }
  };

  const handleEditSuccess = () => {
    setEditTarget(null);
    fetchEmployees();
    onSuccess();
  };

  const handleAddSuccess = () => {
    setIsAddOpen(false);
    fetchEmployees();
    onSuccess();
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Manage Employees</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Header with Add button */}
            <div className="flex items-center justify-end">
              <Button size="sm" onClick={() => setIsAddOpen(true)}>
                <Plus className="h-4 w-4 mr-1" />
                Add Employee
              </Button>
            </div>

            {/* Employee Table */}
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : employees.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                <p>No employees added yet</p>
              </div>
            ) : (
              <>
                <div className="border rounded-lg">
                  <Table>
                    <TableHeader className="bg-muted">
                      <TableRow>
                        <TableHead className="rounded-tl-lg">Name</TableHead>
                        <TableHead>Aliases</TableHead>
                        <TableHead className="w-[100px] rounded-tr-lg">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {paginatedEmployees.map((employee) => (
                        <TableRow key={employee.id}>
                          <TableCell className="font-medium">
                            {employee.canonicalName}
                          </TableCell>
                          <TableCell>
                            {employee.nameVariants && employee.nameVariants.length > 0 ? (
                              <div className="flex flex-wrap gap-1">
                                {employee.nameVariants.slice(0, 2).map((variant) => (
                                  <Badge
                                    key={variant}
                                    variant="outline"
                                    className="text-xs font-normal"
                                  >
                                    {variant}
                                  </Badge>
                                ))}
                                {employee.nameVariants.length > 2 && (
                                  <Badge
                                    variant="outline"
                                    className="text-xs font-normal"
                                  >
                                    +{employee.nameVariants.length - 2}
                                  </Badge>
                                )}
                              </div>
                            ) : (
                              <span className="text-muted-foreground text-sm">—</span>
                            )}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-1">
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8"
                                onClick={() => setEditTarget(employee)}
                              >
                                <Pencil className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8 hover:text-destructive"
                                onClick={() => setDeleteTarget(employee)}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>

                {/* Pagination */}
                {totalPages > 1 && (
                  <div className="flex items-center justify-between pt-2">
                    <p className="text-xs text-muted-foreground">
                      Showing {startIndex + 1}-{Math.min(startIndex + ITEMS_PER_PAGE, employees.length)} of {employees.length}
                    </p>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="outline"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => setCurrentPage((p) => p - 1)}
                        disabled={currentPage === 1}
                      >
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-xs text-muted-foreground px-2">
                        {currentPage} / {totalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => setCurrentPage((p) => p + 1)}
                        disabled={currentPage === totalPages}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* Add Employee Modal */}
      <AddEmployeeModal
        open={isAddOpen}
        onOpenChange={setIsAddOpen}
        onSuccess={handleAddSuccess}
      />

      {/* Edit Employee Modal */}
      <EditEmployeeModal
        employee={editTarget}
        open={!!editTarget}
        onOpenChange={(open) => !open && setEditTarget(null)}
        onSuccess={handleEditSuccess}
      />

      {/* Delete Confirmation */}
      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Remove Employee</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to remove {deleteTarget?.canonicalName} from
              the review tracker?
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Remove
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
