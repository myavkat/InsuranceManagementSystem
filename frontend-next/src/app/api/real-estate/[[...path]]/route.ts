import { NextRequest } from "next/server";
import { bffProxy } from "@/lib/api/bff-proxy";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> },
) {
  const { path } = await params;
  return bffProxy(request, "real-estate", path ?? []);
}

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> },
) {
  const { path } = await params;
  return bffProxy(request, "real-estate", path ?? []);
}

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> },
) {
  const { path } = await params;
  return bffProxy(request, "real-estate", path ?? []);
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ path?: string[] }> },
) {
  const { path } = await params;
  return bffProxy(request, "real-estate", path ?? []);
}
