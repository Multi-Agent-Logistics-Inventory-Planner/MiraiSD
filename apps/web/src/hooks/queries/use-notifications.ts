"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import {
  getNotifications,
  searchNotifications,
  getNotificationCounts,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  deleteNotification,
  resolveNotification,
  unresolveNotification,
  markAsUserRead,
} from "@/lib/api/notifications";
import { useToast } from "@/hooks/use-toast";
import type { NotificationSearchParams } from "@/types/api";

export function useNotifications(page: number = 0, size: number = 50) {
  return useQuery({
    queryKey: ["notifications", { page, size }],
    queryFn: () => getNotifications(undefined, page, size),
    select: (data) => data.content,
  });
}

export function useNotificationSearch(params: NotificationSearchParams) {
  return useQuery({
    queryKey: ["notifications", "search", params],
    queryFn: () => searchNotifications(params),
  });
}

export function useNotificationCounts() {
  return useQuery({
    queryKey: ["notifications", "counts"],
    queryFn: () => getNotificationCounts(),
  });
}

export function useMarkNotificationAsRead() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: markNotificationAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "Notification marked as read", variant: "success" });
    },
    onError: (error) => {
      toast({
        title: "Failed to mark notification as read",
        description: error instanceof Error ? error.message : "Unknown error",
        variant: "destructive",
      });
    },
  });
}

export function useMarkAllNotificationsAsRead() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: () => markAllNotificationsAsRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "All notifications marked as read", variant: "success" });
    },
    onError: (error) => {
      toast({
        title: "Failed to mark all as read",
        description: error instanceof Error ? error.message : "Unknown error",
        variant: "destructive",
      });
    },
  });
}

export function useDeleteNotification() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: deleteNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "Notification deleted", variant: "success" });
    },
    onError: (error) => {
      toast({
        title: "Failed to delete notification",
        description: error instanceof Error ? error.message : "Unknown error",
        variant: "destructive",
      });
    },
  });
}

export function useResolveNotification() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: resolveNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "Notification resolved", variant: "success" });
    },
    onError: (error) => {
      toast({
        title: "Failed to resolve notification",
        description: error instanceof Error ? error.message : "Unknown error",
        variant: "destructive",
      });
    },
  });
}

export function useUnresolveNotification() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: unresolveNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "Notification reopened", variant: "success" });
    },
    onError: (error) => {
      toast({
        title: "Failed to reopen notification",
        description: error instanceof Error ? error.message : "Unknown error",
        variant: "destructive",
      });
    },
  });
}

export function useMarkAsUserRead() {
  const queryClient = useQueryClient();
  const { toast } = useToast();

  return useMutation({
    mutationFn: markAsUserRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      toast({ title: "Marked as read", variant: "success" });
    },
    onError: (error) => {
      toast({
        title: "Failed to mark as read",
        description: error instanceof Error ? error.message : "Unknown error",
        variant: "destructive",
      });
    },
  });
}
