import { NextResponse } from "next/server";

// Handle all HTTP methods for /api/customers/*
export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Customers BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
