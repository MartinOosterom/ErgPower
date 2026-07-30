import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * Renders the agent's Markdown answers safely — react-markdown produces React nodes and does not inject
 * raw HTML, and remark-gfm adds GitHub tables / task lists / strikethrough. Used in the chat panels;
 * the coach panel stays plain prose (change ai-language-and-markdown).
 */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="md">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  )
}
