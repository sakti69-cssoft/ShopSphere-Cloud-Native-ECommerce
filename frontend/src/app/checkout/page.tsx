"use client";
import {RequireAuth} from "@/components/RequireAuth";import {CheckoutExperience} from "@/components/CheckoutExperience";
function CheckoutContent(){return <CheckoutExperience/>}
export default function Checkout(){return <RequireAuth><CheckoutContent/></RequireAuth>}
