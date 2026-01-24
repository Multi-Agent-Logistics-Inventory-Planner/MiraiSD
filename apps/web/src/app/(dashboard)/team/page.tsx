"use client";

import { useState, useEffect, useCallback } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  TeamTable,
  TeamFilters,
  InviteMemberDialog,
  EditMemberDialog,
  TeamMemberRow,
} from "@/components/team";
import { useToast } from "@/hooks/use-toast";
import { getSupabaseClient } from "@/lib/supabase";
import { getUsers, getUserLastAudit, deleteUser } from "@/lib/api/users";
import {
  getPendingInvitations,
  resendInvitation,
  cancelInvitation,
} from "@/lib/api/invitations";
import { User, Invitation } from "@/types/api";

export default function TeamPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [isInviteDialogOpen, setIsInviteDialogOpen] = useState(false);
  const [tableData, setTableData] = useState<TeamMemberRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingMember, setEditingMember] = useState<User | null>(null);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const supabase = getSupabaseClient();
  const { toast } = useToast();

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const session = supabase ? await supabase.auth.getSession() : null;
      if (!session?.data.session?.access_token) return;

      const [usersData, invitationsData] = await Promise.all([
        getUsers(),
        getPendingInvitations().catch(() => [] as Invitation[]),
      ]);

      const membersWithAudit = await Promise.all(
        usersData.map(async (user) => {
          const lastAudit = await getUserLastAudit(user.id).catch(() => null);
          return {
            id: user.id,
            fullName: user.fullName,
            email: user.email,
            role: user.role,
            status: "active" as const,
            lastAudit,
            createdAt: user.createdAt,
            type: "member" as const,
          };
        })
      );

      const invitationRows: TeamMemberRow[] = invitationsData.map((inv) => ({
        id: inv.id,
        fullName: "-",
        email: inv.email,
        role: inv.role,
        status: "pending" as const,
        lastAudit: null,
        createdAt: inv.invitedAt,
        type: "invitation" as const,
      }));

      setTableData([...membersWithAudit, ...invitationRows]);
    } catch (error) {
      // Error handled silently - data will remain empty
    } finally {
      setIsLoading(false);
    }
  }, [supabase]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

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

  const handleEditMember = async (row: TeamMemberRow) => {
    if (row.type !== "member") return;
    try {
      const users = await getUsers();
      const user = users.find((u) => u.id === row.id);
      if (user) {
        setEditingMember(user);
        setIsEditDialogOpen(true);
      }
    } catch (error) {
      // Error handled silently
    }
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
      fetchData();
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
      <h1 className="text-2xl font-semibold tracking-tight">Team</h1>

      <TeamFilters
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        onInviteClick={() => setIsInviteDialogOpen(true)}
      />

      <Card className="py-0">
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
        onSuccess={fetchData}
      />

      <EditMemberDialog
        member={editingMember}
        open={isEditDialogOpen}
        onOpenChange={setIsEditDialogOpen}
        onSuccess={fetchData}
      />
    </div>
  );
}
