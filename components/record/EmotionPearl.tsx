"use client"

import { useId } from "react"

type EmotionPearlProps = {
  color?: string
}

export function EmotionPearl({ color = "#D4D4D8" }: EmotionPearlProps) {
  const id = useId()
  const lightGradient = `${id}-light`
  const darkGradient = `${id}-dark`

  return (
    <svg
      className="w-full max-w-[250px]"
      viewBox="0 0 250 250"
      fill="none"
      aria-hidden="true"
      role="presentation"
    >
      {/* Solid fill — emotion color */}
      <path
        d="M37.6471 162.647C16.855 114.404 39.1088 58.4392 87.3523 37.6471C135.596 16.855 191.56 39.1088 212.352 87.3523C233.144 135.596 210.891 191.56 162.647 212.352C114.404 233.144 58.4392 210.891 37.6471 162.647Z"
        fill={color}
        style={{ transition: "fill 300ms ease" }}
      />
      {/* Light gradient overlay */}
      <path
        d="M37.6471 162.647C16.855 114.404 39.1088 58.4392 87.3523 37.6471C135.596 16.855 191.56 39.1088 212.352 87.3523C233.144 135.596 210.891 191.56 162.647 212.352C114.404 233.144 58.4392 210.891 37.6471 162.647Z"
        fill={`url(#${lightGradient})`}
        fillOpacity={0.2}
      />
      {/* Dark gradient overlay */}
      <path
        d="M37.6471 162.647C16.855 114.404 39.1088 58.4392 87.3523 37.6471C135.596 16.855 191.56 39.1088 212.352 87.3523C233.144 135.596 210.891 191.56 162.647 212.352C114.404 233.144 58.4392 210.891 37.6471 162.647Z"
        fill={`url(#${darkGradient})`}
        fillOpacity={0.2}
        style={{ mixBlendMode: "plus-darker" }}
      />
      {/* Shadow ring */}
      <path
        d="M87.3523 37.6471C135.596 16.855 191.56 39.1088 212.352 87.3523C233.144 135.596 210.891 191.56 162.647 212.352C114.404 233.144 58.4392 210.891 37.6471 162.647C16.855 114.404 39.1088 58.4392 87.3523 37.6471ZM89.2347 42.0147C45.8155 60.7276 26.6299 113.051 46.3824 158.882C61.9764 195.065 109.816 209.227 153.235 190.514C196.654 171.801 219.211 127.3 203.617 91.1171C183.865 45.2857 132.654 23.3019 89.2347 42.0147Z"
        fill="black"
        fillOpacity={0.05}
        style={{ mixBlendMode: "plus-darker" }}
      />
      {/* Highlight reflection */}
      <path
        d="M116.647 62.6394C120.518 71.5386 111.944 78.123 99.9009 83.362C87.8577 88.601 76.9064 90.5107 73.0351 81.6115C69.1637 72.7123 75.7883 61.251 87.8314 56.012C99.8745 50.773 112.776 53.7402 116.647 62.6394Z"
        fill="white"
        fillOpacity={0.1}
      />
      <defs>
        <linearGradient
          id={lightGradient}
          x1="87.3524"
          y1="37.6469"
          x2="162.647"
          y2="212.352"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="white" />
          <stop offset="0.5" stopColor="white" stopOpacity={0} />
        </linearGradient>
        <linearGradient
          id={darkGradient}
          x1="87.3524"
          y1="37.6469"
          x2="162.647"
          y2="212.352"
          gradientUnits="userSpaceOnUse"
        >
          <stop offset="0.5" stopOpacity={0} />
          <stop offset="1" />
        </linearGradient>
      </defs>
    </svg>
  )
}
