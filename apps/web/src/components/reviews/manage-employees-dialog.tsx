"use client";

import { useState, useEffect, KeyboardEvent } from "react";
import { Loader2, Pencil, X, ChevronLeft, ChevronRight, Check, UserCheck, UserX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Switch } from "@/components/ui/switch";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  DataTableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  getAllUsersForReviewManagement,
  updateUserReviewTracking,
} from "@/lib/api/reviews";
import { User } from "@/types/api";

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

interface EditUserModalProps {
  user: User | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

function EditUserModal({
  user,
  open,
  onOpenChange,
  onSuccess,
}: EditUserModalProps) {
  const [canonicalName, setCanonicalName] = useState("");
  const [aliases, setAliases] = useState<string[]>([]);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    if (user) {
      // Default canonical name to first name if not set
      setCanonicalName(user.canonicalName || user.fullName.split(" ")[0]);
      setAliases(user.nameVariants || []);
    }
  }, [user]);

  const handleSave = async () => {
    if (!user || !canonicalName.trim()) return;

    setIsSaving(true);
    try {
      await updateUserReviewTracking(user.id, {
        canonicalName: canonicalName.trim(),
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
          <DialogTitle>Edit Review Settings</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 pt-2">
          <div className="space-y-1">
            <Label className="text-muted-foreground text-sm">User</Label>
            <p className="font-medium">{user?.fullName}</p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="canonical-name">Review Name</Label>
            <p className="text-xs text-muted-foreground">
              Primary name to search for in reviews
            </p>
            <Input
              id="canonical-name"
              value={canonicalName}
              onChange={(e) => setCanonicalName(e.target.value)}
              disabled={isSaving}
              placeholder="e.g., Quincy"
            />
          </div>
          <div className="space-y-2">
            <Label>Name Aliases</Label>
            <p className="text-xs text-muted-foreground">
              Additional names or spellings used in reviews
            </p>
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
            <Button onClick={handleSave} disabled={isSaving}>
              {isSaving && <Loader2 className="h-4 w-4 animate-spin mr-2" />}
              Save
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
  const [users, setUsers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  const [editTarget, setEditTarget] = useState<User | null>(null);
  const [togglingUserId, setTogglingUserId] = useState<string | null>(null);

  const totalPages = Math.ceil(users.length / ITEMS_PER_PAGE);
  const startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
  const paginatedUsers = users.slice(startIndex, startIndex + ITEMS_PER_PAGE);

  const fetchUsers = async () => {
    setIsLoading(true);
    try {
      const data = await getAllUsersForReviewManagement();
      // Sort by tracked status (tracked first) then by name
      const sorted = data.sort((a, b) => {
        if (a.isReviewTracked === b.isReviewTracked) {
          return a.fullName.localeCompare(b.fullName);
        }
        return a.isReviewTracked ? -1 : 1;
      });
      setUsers(sorted);
    } catch (error) {
      // Error handled silently
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (open) {
      fetchUsers();
      setCurrentPage(1);
    }
  }, [open]);

  const handleToggleTracking = async (user: User) => {
    setTogglingUserId(user.id);
    try {
      await updateUserReviewTracking(user.id, {
        isReviewTracked: !user.isReviewTracked,
      });
      fetchUsers();
      onSuccess();
    } catch (error) {
      // Error handled silently
    } finally {
      setTogglingUserId(null);
    }
  };

  const handleEditSuccess = () => {
    setEditTarget(null);
    fetchUsers();
    onSuccess();
  };

  const trackedCount = users.filter((u) => u.isReviewTracked).length;

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>Manage Review Tracking</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Summary */}
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <UserCheck className="h-4 w-4" />
              <span>{trackedCount} of {users.length} users tracked for reviews</span>
            </div>

            {/* User Table */}
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : users.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                <p>No users found</p>
              </div>
            ) : (
              <>
                <div className="border rounded-lg">
                  <Table>
                    <DataTableHeader>
                      <TableHead className="rounded-l-lg">User</TableHead>
                      <TableHead>Aliases</TableHead>
                      <TableHead className="w-[100px] text-center">Tracked</TableHead>
                      <TableHead className="w-[60px] rounded-r-lg">Edit</TableHead>
                    </DataTableHeader>
                    <TableBody>
                      {paginatedUsers.map((user) => (
                        <TableRow key={user.id}>
                          <TableCell className="rounded-l-lg">
                            <div className="flex flex-col">
                              <span className="font-medium">{user.fullName}</span>
                              <span className="text-xs text-muted-foreground">{user.canonicalName}</span>
                            </div>
                          </TableCell>
                          <TableCell>
                            {user.nameVariants && user.nameVariants.length > 0 ? (
                              <div className="flex flex-wrap gap-1">
                                {user.nameVariants.slice(0, 2).map((variant) => (
                                  <Badge
                                    key={variant}
                                    variant="outline"
                                    className="text-xs font-normal"
                                  >
                                    {variant}
                                  </Badge>
                                ))}
                                {user.nameVariants.length > 2 && (
                                  <Badge
                                    variant="outline"
                                    className="text-xs font-normal"
                                  >
                                    +{user.nameVariants.length - 2}
                                  </Badge>
                                )}
                              </div>
                            ) : (
                              <span className="text-muted-foreground text-sm">—</span>
                            )}
                          </TableCell>
                          <TableCell className="text-center">
                            {togglingUserId === user.id ? (
                              <Loader2 className="h-4 w-4 animate-spin mx-auto" />
                            ) : (
                              <Switch
                                checked={user.isReviewTracked || false}
                                onCheckedChange={() => handleToggleTracking(user)}
                              />
                            )}
                          </TableCell>
                          <TableCell className="rounded-r-lg">
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8"
                              onClick={() => setEditTarget(user)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
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
                      Showing {startIndex + 1}-{Math.min(startIndex + ITEMS_PER_PAGE, users.length)} of {users.length}
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

      {/* Edit User Modal */}
      <EditUserModal
        user={editTarget}
        open={!!editTarget}
        onOpenChange={(open) => !open && setEditTarget(null)}
        onSuccess={handleEditSuccess}
      />
    </>
  );
}
