"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Card, CardContent } from "@/components/ui/card";
import {
  TeamTable,
  TeamFilters,
  InviteMemberDialog,
  EditMemberDialog,
  TeamMemberRow,
} from "@/components/team";
import { useToast } from "@/hooks/use-toast";
import { useTeamData } from "@/hooks/queries/use-team-data";
import { deleteUser } from "@/lib/api/users";
import {
  resendInvitation,
  cancelInvitation,
} from "@/lib/api/invitations";
import { User, UserRole } from "@/types/api";
import { SidebarTrigger } from "@/components/ui/sidebar";

export default function TeamPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [isInviteDialogOpen, setIsInviteDialogOpen] = useState(false);
  const [editingMember, setEditingMember] = useState<User | null>(null);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: tableData = [], isLoading } = useTeamData();

  const invalidateTeamData = () => {
    queryClient.invalidateQueries({ queryKey: ["team-data"] });
  };

  const filteredData = tableData.filter((row) => {
    return (
      row.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      row.email.toLowerCase().includes(searchQuery.toLowerCase())
    );
  });

  const handleResendInvite = async (email: string) => {
    try {
      await resendInvitation(email);
      toast({ title: "Invitation resent successfully" });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to resend invitation";
      toast({
        title: "Failed to resend invitation",
        description: message,
        variant: "destructive",
      });
    }
  };

  const handleEditMember = (row: TeamMemberRow) => {
    if (row.type !== "member") return;
    const user: User = {
      id: row.id,
      fullName: row.fullName,
      email: row.email,
      role: row.role as UserRole,
      createdAt: row.createdAt,
      updatedAt: row.createdAt,
    };
    setEditingMember(user);
    setIsEditDialogOpen(true);
  };

  const handleDelete = async (row: TeamMemberRow) => {
    try {
      if (row.type === "member") {
        await deleteUser(row.id);
        toast({ title: "Team member removed successfully" });
      } else {
        await cancelInvitation(row.email);
        toast({ title: "Invitation cancelled successfully" });
      }
      invalidateTeamData();
    } catch (error) {
      const message = error instanceof Error ? error.message : "Operation failed";
      toast({
        title: row.type === "member" ? "Failed to remove team member" : "Failed to cancel invitation",
        description: message,
        variant: "destructive",
      });
    }
  };

  return (
    <div className="flex flex-col p-4 md:p-8 space-y-4">
      <div className="flex items-center gap-2">
        <SidebarTrigger className="md:hidden" />
        <h1 className="text-2xl font-semibold tracking-tight">Team</h1>
      </div>

      <TeamFilters
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        onInviteClick={() => setIsInviteDialogOpen(true)}
      />

      <Card className="p-2 border-none">
        <CardContent className="p-0">
          <TeamTable
            data={filteredData}
            isLoading={isLoading}
            onEdit={handleEditMember}
            onDelete={handleDelete}
            onResendInvite={handleResendInvite}
          />
        </CardContent>
      </Card>

      <InviteMemberDialog
        open={isInviteDialogOpen}
        onOpenChange={setIsInviteDialogOpen}
        onSuccess={invalidateTeamData}
      />

      <EditMemberDialog
        member={editingMember}
        open={isEditDialogOpen}
        onOpenChange={setIsEditDialogOpen}
        onSuccess={invalidateTeamData}
      />
    </div>
  );
}
