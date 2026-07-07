"use client";

import { useEffect } from "react";
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
  const { sidebarOpen, toggleSidebar, setSidebarOpen } = useUIStore();

  // Close sidebar on mobile at initial load
  useEffect(() => {
    if (window.innerWidth < 1024) {
      setSidebarOpen(false);
    }
  }, [setSidebarOpen]);

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
            : "w-64 -translate-x-full lg:w-16 lg:translate-x-0",
        )}
      >
        {/* Sidebar header */}
        <div className="flex h-14 shrink-0 items-center border-b px-3">
          {sidebarOpen ? (
            <>
              <Link
                href="/dashboard"
                className="text-lg font-semibold tracking-tight"
              >
                IMS
              </Link>
              <div className="flex-1" />
              <Button
                variant="ghost"
                size="icon"
                onClick={toggleSidebar}
                aria-label="Collapse sidebar"
                className="hidden lg:inline-flex"
              >
                <PanelLeftClose />
              </Button>
            </>
          ) : (
            <Button
              variant="ghost"
              size="icon"
              onClick={toggleSidebar}
              aria-label="Expand sidebar"
              className="mx-auto"
            >
              <PanelLeft />
            </Button>
          )}
        </div>

        {/* Navigation links */}
        <nav className="flex-1 space-y-1 p-2">
          {navItems.map((item) => {
            const isActive =
              pathname === item.href || pathname.startsWith(item.href + "/");
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  sidebarOpen ? "justify-start" : "justify-center",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground",
                )}
              >
                <item.icon className="size-4 shrink-0" />
                {sidebarOpen && <span className="truncate">{item.label}</span>}
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
          sidebarOpen && "hidden",
        )}
        aria-label="Open sidebar"
      >
        <PanelLeft />
      </Button>
    </>
  );
}
