import { useState } from "react"
import {
  Button,
  Dialog,
  DialogTrigger,
  FieldError,
  Form,
  Heading,
  Input,
  Label,
  Modal,
  ModalOverlay,
  Text,
  TextField,
} from "react-aria-components"
import { useApi } from "../api/ApiContext.tsx"

function validateUrl(value: string): string | null {
  if (!value.trim()) return null
  try {
    const url = new URL(value)
    return url.protocol === "http:" || url.protocol === "https:" ? null : "Use an http(s) URL"
  } catch {
    return "Not a valid URL"
  }
}

/** Chooses the backend. An empty field means the default. */
export function SettingsDialog() {
  const { baseUrl, defaultBaseUrl, override, setOverride } = useApi()
  const [draft, setDraft] = useState(override ?? "")

  return (
    <DialogTrigger onOpenChange={(open) => open && setDraft(override ?? "")}>
      <Button className="react-aria-Button quiet" aria-label="Settings">
        <span className="mono">{baseUrl}</span> ⚙
      </Button>
      <ModalOverlay isDismissable>
        <Modal>
          <Dialog>
            {({ close }) => (
              <Form
                onSubmit={(event) => {
                  event.preventDefault()
                  setOverride(draft.trim() || null)
                  close()
                }}
              >
                <Heading slot="title">Settings</Heading>
                <TextField value={draft} onChange={setDraft} validate={validateUrl} type="url">
                  <Label>Backend base URL</Label>
                  <Input placeholder={defaultBaseUrl} />
                  <Text slot="description">
                    Leave empty for the default: <span className="mono">{defaultBaseUrl}</span>
                  </Text>
                  <FieldError />
                </TextField>
                <div className="actions">
                  <Button onPress={() => setDraft("")}>Reset to default</Button>
                  <span className="spacer" />
                  <Button onPress={close}>Cancel</Button>
                  <Button type="submit" className="react-aria-Button primary">
                    Save
                  </Button>
                </div>
              </Form>
            )}
          </Dialog>
        </Modal>
      </ModalOverlay>
    </DialogTrigger>
  )
}
