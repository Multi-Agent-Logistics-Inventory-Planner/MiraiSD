"use client";

import { useState, useEffect, useCallback, Suspense } from "react";
import {
  Search,
  Users as UsersIcon,
  UserCheck,
  Clock,
  Shield,
  Pencil,
  Trash2,
  RefreshCw,
  Loader2,
  Mail,
} from "lucide-react";
import { DashboardHeader } from "@/components/dashboard-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import Loading from "./loading";
import { getSupabaseClient } from "@/lib/supabase";
import { getUsers, getUserLastAudit, deleteUser } from "@/lib/api/users";
import { getPendingInvitations, resendInvitation, cancelInvitation } from "@/lib/api/invitations";
import { EditUserDialog } from "@/components/users/edit-user-dialog";
import { User, Invitation } from "@/types/api";

type UserTableRow = {
  id: string;
  fullName: string;
  email: string;
  role: string;
  status: "active" | "pending";
  lastAudit: string | null;
  createdAt: string;
  type: "user" | "invitation";
};

function getStatusColor(status: "active" | "pending") {
  switch (status) {
    case "active":
      return "bg-green-100 text-green-700";
    case "pending":
      return "bg-amber-100 text-amber-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

function getRoleColor(role: "admin" | "employee") {
  switch (role) {
    case "admin":
      return "bg-purple-100 text-purple-700";
    case "employee":
      return "bg-gray-100 text-gray-700";
    default:
      return "bg-gray-100 text-gray-700";
  }
}

// Access control permissions
const accessPermissions = [
  {
    role: "Admin",
    permissions: [
      "View Inventory",
      "Edit Inventory",
      "Delete Items",
      "View Shipments",
      "Manage Shipments",
      "View Analytics",
      "View Alerts",
      "Manage Users",
      "Access Settings",
      "View Audit Logs",
    ],
  },
  {
    role: "Employee",
    permissions: [
      "View Inventory",
      "Edit Inventory",
      "View Shipments",
      "View Alerts",
    ],
  },
];

export default function UsersPage() {
  const [searchQuery, setSearchQuery] = useState("");
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<string>("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [inviteSuccess, setInviteSuccess] = useState(false);
  const supabase = getSupabaseClient();

  // New state for real data
  const [tableData, setTableData] = useState<UserTableRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    try {
      const session = supabase ? await supabase.auth.getSession() : null;
      if (!session?.data.session?.access_token) return;

      const [usersData, invitationsData] = await Promise.all([
        getUsers(),
        getPendingInvitations().catch(() => [] as Invitation[]),
      ]);

      // Fetch last audit for each user
      const usersWithAudit = await Promise.all(
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
            type: "user" as const,
          };
        })
      );

      // Map invitations to table rows
      const invitationRows: UserTableRow[] = invitationsData.map((inv) => ({
        id: inv.id,
        fullName: "-",
        email: inv.email,
        role: inv.role,
        status: "pending" as const,
        lastAudit: null,
        createdAt: inv.invitedAt,
        type: "invitation" as const,
      }));

      setTableData([...usersWithAudit, ...invitationRows]);
    } catch (error) {
      console.error("Failed to fetch users:", error);
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

  const totalUsers = tableData.filter((r) => r.type === "user").length;
  const activeUsers = tableData.filter((r) => r.status === "active").length;
  const pendingInvites = tableData.filter((r) => r.status === "pending").length;
  const adminCount = tableData.filter((r) => r.role === "ADMIN" || r.role === "admin").length;

  const resetInviteForm = () => {
    setInviteEmail("");
    setInviteRole("");
    setInviteError(null);
    setInviteSuccess(false);
  };

  const handleInviteUser = async () => {
    if (!inviteEmail || !inviteRole) {
      setInviteError("Email and role are required");
      return;
    }

    setIsSubmitting(true);
    setInviteError(null);

    try {
      const session = supabase ? await supabase.auth.getSession() : null;
      const accessToken = session?.data.session?.access_token;

      if (!accessToken) {
        setInviteError("You must be logged in to invite users");
        setIsSubmitting(false);
        return;
      }

      const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:4000";
      const response = await fetch(`${backendUrl}/api/admin/invitations`, {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: inviteEmail,
          role: inviteRole.toUpperCase(),
        }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.message || "Failed to send invitation");
      }

      setInviteSuccess(true);
      setTimeout(() => {
        setIsAddDialogOpen(false);
        resetInviteForm();
        fetchData();
      }, 2000);
    } catch (error) {
      setInviteError(error instanceof Error ? error.message : "Failed to send invitation");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResendInvite = async (email: string) => {
    try {
      await resendInvitation(email);
    } catch (error) {
      console.error("Failed to resend invitation:", error);
    }
  };

  const handleEditUser = async (row: UserTableRow) => {
    if (row.type !== "user") return;
    try {
      const users = await getUsers();
      const user = users.find((u) => u.id === row.id);
      if (user) {
        setEditingUser(user);
        setIsEditDialogOpen(true);
      }
    } catch (error) {
      console.error("Failed to fetch user for editing:", error);
    }
  };

  const handleDelete = async (row: UserTableRow) => {
    try {
      if (row.type === "user") {
        await deleteUser(row.id);
      } else {
        await cancelInvitation(row.email);
      }
      fetchData();
    } catch (error) {
      console.error("Failed to delete:", error);
    }
  };

  return (
    <Suspense fallback={<Loading />}>
      <div className="flex flex-col">
        <DashboardHeader
          title="Users"
          description="User management and access control"
        />
        <main className="flex-1 space-y-6 p-4 md:p-6">
          {/* Stats Cards */}
          {/* <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">
                  Total Users
                </CardTitle>
                <UsersIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{totalUsers}</div>
                <p className="text-xs text-muted-foreground">
                  Registered users
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">
                  Active Users
                </CardTitle>
                <UserCheck className="h-4 w-4 text-green-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-green-600">
                  {activeUsers}
                </div>
                <p className="text-xs text-muted-foreground">
                  Currently on shift
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">
                  Pending Invites
                </CardTitle>
                <Clock className="h-4 w-4 text-amber-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-amber-600">
                  {pendingInvites}
                </div>
                <p className="text-xs text-muted-foreground">Awaiting acceptance</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-sm font-medium">
                  Administrators
                </CardTitle>
                <Shield className="h-4 w-4 text-purple-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-purple-600">
                  {adminCount}
                </div>
                <p className="text-xs text-muted-foreground">
                  Full access users
                </p>
              </CardContent>
            </Card>
          </div> */}

          {/* Filters and Actions */}
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search users..."
                className="pl-8"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <Dialog
              open={isAddDialogOpen}
              onOpenChange={(open) => {
                setIsAddDialogOpen(open);
                if (!open) resetInviteForm();
              }}
            >
              <DialogTrigger asChild>
                <Button>
                  <Mail className="mr-2 h-4 w-4" />
                  Invite User
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Invite New User</DialogTitle>
                  <DialogDescription>
                    Send an email invitation. The user will set their name and password when they accept.
                  </DialogDescription>
                </DialogHeader>
                <div className="grid gap-4 py-4">
                  <div className="grid gap-2">
                    <Label htmlFor="email">Email</Label>
                    <Input
                      id="email"
                      type="email"
                      placeholder="user@company.com"
                      value={inviteEmail}
                      onChange={(e) => setInviteEmail(e.target.value)}
                      disabled={isSubmitting}
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="role">Role</Label>
                    <Select
                      value={inviteRole}
                      onValueChange={setInviteRole}
                      disabled={isSubmitting}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder="Select role" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="ADMIN">Admin</SelectItem>
                        <SelectItem value="EMPLOYEE">Employee</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {inviteError && (
                    <Alert variant="destructive">
                      <AlertDescription>{inviteError}</AlertDescription>
                    </Alert>
                  )}

                  {inviteSuccess && (
                    <Alert className="bg-green-50 border-green-200 text-green-800">
                      <AlertDescription>
                        Invitation sent successfully!
                      </AlertDescription>
                    </Alert>
                  )}
                </div>
                <DialogFooter>
                  <Button
                    variant="outline"
                    onClick={() => {
                      setIsAddDialogOpen(false);
                      resetInviteForm();
                    }}
                    disabled={isSubmitting}
                  >
                    Cancel
                  </Button>
                  <Button
                    onClick={handleInviteUser}
                    disabled={isSubmitting || inviteSuccess}
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Sending...
                      </>
                    ) : (
                      "Send Invitation"
                    )}
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          </div>

          {/* Users Content */}
          <Tabs defaultValue="users" className="space-y-4">
            <TabsList>
              <TabsTrigger value="users">Users</TabsTrigger>
              <TabsTrigger value="access-control">Access Control</TabsTrigger>
            </TabsList>
            <TabsContent value="users">
              <Card>
                {isLoading ? (
                  <div className="flex items-center justify-center p-8">
                    <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                  </div>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Name</TableHead>
                        <TableHead>Email</TableHead>
                        <TableHead>Role</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Last Audit</TableHead>
                        <TableHead className="w-[120px]">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredData.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell className="font-medium">
                            {row.fullName}
                          </TableCell>
                          <TableCell>{row.email}</TableCell>
                          <TableCell>
                            <Badge
                              variant="outline"
                              className={cn("text-xs", getRoleColor(row.role.toLowerCase() as "admin" | "employee"))}
                            >
                              {row.role.charAt(0).toUpperCase() + row.role.slice(1).toLowerCase()}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant="outline"
                              className={cn(
                                "text-xs",
                                getStatusColor(row.status)
                              )}
                            >
                              {row.status.charAt(0).toUpperCase() + row.status.slice(1)}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            {row.lastAudit ? new Date(row.lastAudit).toLocaleDateString() : "-"}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-1">
                              {row.status === "pending" && (
                                <Button
                                  variant="ghost"
                                  size="icon-sm"
                                  title="Resend invitation"
                                  onClick={() => handleResendInvite(row.email)}
                                >
                                  <RefreshCw className="h-4 w-4" />
                                </Button>
                              )}
                              {row.type === "user" && (
                                <Button
                                  variant="ghost"
                                  size="icon-sm"
                                  onClick={() => handleEditUser(row)}
                                >
                                  <Pencil className="h-4 w-4" />
                                </Button>
                              )}
                              <Button
                                variant="ghost"
                                size="icon-sm"
                                className="text-destructive"
                                onClick={() => handleDelete(row)}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </Card>
            </TabsContent>
            <TabsContent value="access-control">
              <div className="grid gap-4 md:grid-cols-3">
                {accessPermissions.map((access) => (
                  <Card key={access.role}>
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2">
                        <Shield className="h-5 w-5" />
                        {access.role}
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <ul className="space-y-2">
                        {access.permissions.map((permission) => (
                          <li
                            key={permission}
                            className="flex items-center gap-2 text-sm"
                          >
                            <div className="h-1.5 w-1.5 rounded-full bg-green-500" />
                            {permission}
                          </li>
                        ))}
                      </ul>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </TabsContent>
          </Tabs>
        </main>
      </div>

      <EditUserDialog
        user={editingUser}
        open={isEditDialogOpen}
        onOpenChange={setIsEditDialogOpen}
        onSuccess={fetchData}
      />
    </Suspense>
  );
}
