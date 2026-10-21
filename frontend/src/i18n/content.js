import vi from '../locales/vi.json'
import { translate } from './index.js'

// Preserve the named content API while translating on every reactive read.
// Reading copy.title inside a render/computed tracks the active locale.
export function defineContent(namespace, { methods = {}, parameters = {} } = {}) {
    const source = namespace.split('.').reduce((value, key) => value?.[key], vi)
    if (!source || typeof source !== 'object') throw new Error(`Unknown content: ${namespace}`)
    function build(messages, parent = '') {
        const result = {}
        for (const [key, value] of Object.entries(messages)) {
            const path = parent ? `${parent}.${key}` : key
            const messageKey = `${namespace}.${path}`
            if (typeof value === 'object') result[key] = build(value, path)
            else if (methods[path]) {
                const mapper = methods[path]
                result[key] = (input = {}) => translate(messageKey, mapper === true ? input : mapper(input))
            } else {
                Object.defineProperty(result, key, {
                    enumerable: true,
                    get: () => translate(messageKey, parameters[path]?.() ?? {}),
                })
            }
        }
        return result
    }
    return build(source)
}
