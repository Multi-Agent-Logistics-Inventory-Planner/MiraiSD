import { redirect } from "next/navigation";

export default function NotificationsPage() {
  redirect("/analytics?tab=notifications");
}
