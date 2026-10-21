// Catch copy hidden inside script assignments, ternaries and template expressions.
// Structural literals (route names, CSS classes, API enums) remain in the component.
const vietnameseText = /[À-ÖØ-öø-ỹ]/u

export default {
  meta: {
    type: 'suggestion',
    schema: [],
    messages: {
      externalize: 'Move UI wording to src/content and reference a named key here.',
    },
  },
  create(context) {
    const check = (node, value) => {
      if (typeof value === 'string' && value.length > 1 && vietnameseText.test(value)) {
        context.report({ node, messageId: 'externalize' })
      }
    }
    const visitor = {
      Literal: (node) => check(node, node.value),
      TemplateElement: (node) => check(node, node.value.cooked),
    }
    return context.sourceCode.parserServices.defineTemplateBodyVisitor(visitor, visitor)
  },
}
