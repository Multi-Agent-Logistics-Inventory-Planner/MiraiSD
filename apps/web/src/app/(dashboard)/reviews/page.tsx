import { redirect } from "next/navigation";

export default function ReviewsPage() {
  redirect("/team?tab=reviews");
}
