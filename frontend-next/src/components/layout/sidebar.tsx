"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { useUIStore } from "@/lib/store/ui-store";
import { Button } from "@/components/ui/button";
import {
  LayoutDashboard,
  Users,
  Shield,
  Calculator,
  Car,
  PanelLeftClose,
  PanelLeft,
} from "lucide-react";

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

const navItems: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/customers", label: "Customers", icon: Users },
  { href: "/insurances", label: "Insurances", icon: Shield },
  { href: "/estimations", label: "Estimations", icon: Calculator },
  { href: "/vehicles", label: "Vehicles", icon: Car },
];

export function Sidebar() {
  const pathname = usePathname();
  const { sidebarOpen, toggleSidebar } = useUIStore();

  return (
    <>
      {/* Mobile overlay backdrop */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={toggleSidebar}
          aria-hidden="true"
        />
      )}

      {/* Sidebar panel */}
      <aside
        className={cn(
          "flex h-full flex-col border-r bg-sidebar text-sidebar-foreground transition-all duration-300 ease-in-out",
          "fixed top-0 left-0 z-50",
          "lg:static lg:z-auto lg:min-w-0",
          sidebarOpen
            ? "w-64 translate-x-0"
            : "w-64 -translate-x-full lg:w-0 lg:overflow-hidden lg:border-r-0"
        )}
      >
        {/* Sidebar header with collapse button */}
        <div className="flex h-14 items-center justify-between border-b px-4 shrink-0">
          <Link href="/dashboard" className="text-lg font-semibold tracking-tight">
            IMS
          </Link>
          <Button
            variant="ghost"
            size="icon"
            onClick={toggleSidebar}
            aria-label="Toggle sidebar"
            className="hidden lg:inline-flex"
          >
            <PanelLeftClose className="h-5 w-5" />
          </Button>
        </div>

        {/* Navigation links */}
        <nav className={cn("flex-1 space-y-1 p-3", !sidebarOpen && "lg:hidden")}>
          {navItems.map((item) => {
            const isActive =
              pathname === item.href || pathname.startsWith(item.href + "/");
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground"
                )}
              >
                <item.icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      {/* Mobile toggle button (visible when sidebar is closed) */}
      <Button
        variant="outline"
        size="icon"
        onClick={toggleSidebar}
        className={cn(
          "fixed top-3 left-3 z-50 lg:hidden",
          sidebarOpen && "hidden"
        )}
        aria-label="Open sidebar"
      >
        <PanelLeft className="h-5 w-5" />
      </Button>
    </>
  );
}
