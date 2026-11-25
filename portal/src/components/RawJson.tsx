import { Button, Disclosure, DisclosurePanel, Heading } from "react-aria-components"

/** The raw response, collapsed by default. */
export function RawJson({ value, label = "Raw response" }: { value: unknown; label?: string }) {
  return (
    <Disclosure className="raw-json">
      <Heading level={3}>
        <Button slot="trigger">{label}</Button>
      </Heading>
      <DisclosurePanel>
        <pre>{JSON.stringify(value, null, 2)}</pre>
      </DisclosurePanel>
    </Disclosure>
  )
}
