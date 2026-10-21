import { describe, expect, it } from 'vitest'
import { ESLint } from 'eslint'

const eslint = new ESLint()
async function contentErrors(source) {
  const [result] = await eslint.lintText(source, { filePath: 'src/views/CopyFixture.vue' })
  return result.messages.filter(
    ({ ruleId }) =>
      ruleId === 'content/no-inline-ui-copy' || ruleId === 'vue/no-bare-strings-in-template',
  )
}

describe('UI content boundaries', () => {
  it('rejects inline copy in state-dependent labels', async () => {
    expect(
      await contentErrors(`<script setup>const busy = false</script>
        <template><button>{{ busy ? 'Đang kích hoạt…' : 'Kích hoạt' }}</button></template>`),
    ).toHaveLength(2)
  })

  it('rejects script messages and interpolated sentences', async () => {
    expect(
      await contentErrors(
        [
          '<script setup>',
          'const count = 2',
          'const error = "Không thể gửi yêu cầu."',
          'const label = `Đã gửi ${count} đơn.`',
          '</script><template><p>{{ error }} {{ label }}</p></template>',
        ].join('\n'),
      ),
    ).toHaveLength(3)
  })

  it('rejects hardcoded text and accessibility labels', async () => {
    expect(
      await contentErrors('<template><button aria-label="Close dialog">Close</button></template>'),
    ).toHaveLength(2)
  })

  it('allows content references, structural values and display separators', async () => {
    expect(
      await contentErrors(`<script setup>
        import { activation as copy } from '@/content/auth.js'
        const busy = false
        const status = 'PENDING'
        </script><template><button class="button" :disabled="status !== 'PENDING'">
          {{ busy ? copy.actions.submitting : copy.actions.submit }} →
        </button></template>`),
    ).toEqual([])
  })
})
