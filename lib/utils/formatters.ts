/** Full date + time: "Monday, March 15, 2026, 2:30 PM" */
export const dateTimeFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "long",
  year: "numeric",
  month: "long",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
})

/** Short date + time: "Mon, Mar 15, 2026, 2:30 PM" */
export const shortDateTimeFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "short",
  year: "numeric",
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "numeric",
})

/** Time only: "2:30 PM" */
export const timeFormatter = new Intl.DateTimeFormat("en-US", {
  hour: "numeric",
  minute: "numeric",
})
