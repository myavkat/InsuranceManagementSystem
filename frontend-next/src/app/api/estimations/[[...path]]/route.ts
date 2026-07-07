import { NextResponse } from "next/server";

// Handle all HTTP methods for /api/estimations/*
export async function GET() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function POST() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub — POST handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function PUT() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub — PUT handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}

export async function DELETE() {
  return NextResponse.json({
    success: true,
    message: "Estimations BFF stub — DELETE handler. Will proxy to API Gateway in a future sprint.",
    data: null,
  });
}
