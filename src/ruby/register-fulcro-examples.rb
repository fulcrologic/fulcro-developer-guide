(require_relative './fulcro-examples-extension.rb')


Asciidoctor::Extensions.register do
  preprocessor FulcroPreprocessor
end


